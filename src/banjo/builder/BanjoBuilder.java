package banjo.builder;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.osgi.framework.Bundle;

import banjo.editor.Activator;
import banjo.eval.Fail;
import banjo.eval.SlotNotFound;
import banjo.eval.environment.Environment;
import banjo.expr.BadExpr;
import banjo.expr.core.BadCoreExpr;
import banjo.expr.core.BaseCoreExprVisitor;
import banjo.expr.core.Call;
import banjo.expr.core.CoreErrorGatherer;
import banjo.expr.core.CoreExpr;
import banjo.expr.core.CoreExprFactory;
import banjo.expr.core.CoreExprFromFile;
import banjo.expr.core.Let;
import banjo.expr.core.Projection;
import banjo.expr.core.TestAndExampleGatherer;
import banjo.expr.free.FreeExpression;
import banjo.expr.free.FreeExpressionFactory;
import banjo.expr.source.Operator;
import banjo.expr.source.SourceExpr;
import banjo.expr.source.SourceExprFromFile;
import banjo.expr.token.BadIdentifier;
import banjo.expr.token.Identifier;
import banjo.expr.util.FileRange;
import banjo.expr.util.ParserReader;
import banjo.expr.util.SourceFileRange;
import banjo.value.Value;
import fj.Ord;
import fj.P;
import fj.P2;
import fj.data.List;
import fj.data.Option;
import fj.data.Set;
import fj.data.TreeMap;

public class BanjoBuilder extends IncrementalProjectBuilder {
    abstract class BanjoBuilderVisitor {
        Set<IFile> banjoSourceFiles = Set.empty(Ord.stringOrd.contramap(IFile::toString));
        int numberOfSourceFiles = 0;

        void addSource(IResource resource) {
            // Update our list of project ASTs to analyze
            if(isBanjoSource(resource)) {
                banjoSourceFiles = banjoSourceFiles.insert((IFile) resource);
                numberOfSourceFiles++;
            }
        }

        abstract public void collectSources() throws CoreException;
    }

    class BanjoBuilderDeltaVisitor extends BanjoBuilderVisitor implements IResourceDeltaVisitor {

        private IResourceDelta delta;

        public BanjoBuilderDeltaVisitor(IResourceDelta delta) {
            this.delta = delta;
        }

        @Override
        public void collectSources() throws CoreException {
            delta.accept(this);
        }

		@Override
		public boolean visit(IResourceDelta delta) throws CoreException {
			final IResource resource = delta.getResource();
            if((delta.getKind() & (IResourceDelta.ADDED | IResourceDelta.REMOVED | IResourceDelta.CHANGED)) != 0)
                addSource(resource);
			//return true to continue visiting children.
			return true;
		}
	}

    class BanjoBuilderProjectVisitor extends BanjoBuilderVisitor implements IResourceVisitor {
        private IProject project;

        public BanjoBuilderProjectVisitor(IProject project) {
            this.project = project;
        }

        @Override
		public boolean visit(IResource resource) {
            addSource(resource);
			//return true to continue visiting children.
			return true;
		}

        @Override
        public void collectSources() throws CoreException {
            project.accept(this);
        }
	}

	public static final String BUILDER_ID = "banjo.editor.banjoBuilder";
	private static final String MARKER_TYPE = IMarker.PROBLEM;
    public static ExecutorService executor = Executors.newCachedThreadPool();

    private static void addMarker(SourceFileRange sfr, String message, int severity) {
        // Only try to add markers if the source file came from eclipse
        if(!(sfr.sourceFile instanceof EclipseWorkspacePath))
            return;

        IFile file = ((EclipseWorkspacePath) sfr.sourceFile).getFile();

        // Only try to add a marker to a file that still exists
        if(!file.exists())
            return;

        FileRange range = sfr.getFileRange();
		try {
			final IMarker marker = file.createMarker(MARKER_TYPE);
			marker.setAttribute(IMarker.MESSAGE, message);
			marker.setAttribute(IMarker.SEVERITY, severity);
			marker.setAttribute(IMarker.LINE_NUMBER, range.getStartLine());
			marker.setAttribute(IMarker.CHAR_START, range.getStartOffset());
			marker.setAttribute(IMarker.CHAR_END, range.getEndOffset());
		} catch (final CoreException e) {
			Activator.log(e.getStatus());
		}
	}

	public static int calculateLineNumber(IFile file, final int sourceOffset)
			throws CoreException, IOException, UnsupportedEncodingException {
		final long fileLength = EFS.getStore(file.getLocationURI()).fetchInfo().getLength();
		final ParserReader in = new ParserReader(new InputStreamReader(file.getContents(), file.getCharset()), (int) fileLength);
		try {
			in.skip(sourceOffset);
			final int lineNo = in.getCurrentLineNumber();
			return lineNo;
		} finally {
			in.close();
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.core.internal.events.InternalBuilder#build(int,
	 *      java.util.Map, org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	protected IProject[] build(int kind, @SuppressWarnings("rawtypes") Map args, IProgressMonitor monitor)
			throws CoreException {
		if (kind == FULL_BUILD) {
			fullBuild(monitor);
		} else {
			final IResourceDelta delta = getDelta(getProject());
			if (delta == null) {
				fullBuild(monitor);
			} else {
				incrementalBuild(delta, monitor);
			}
		}
		return null;
	}

	@Override
	protected void clean(IProgressMonitor monitor) throws CoreException {
		// delete markers set and files created
        monitor.beginTask("Remove markers", 1000);
        try {
            getProject().deleteMarkers(MARKER_TYPE, true, IResource.DEPTH_INFINITE);
            monitor.worked(1000);
        } finally {
            monitor.done();
        }
	}

    Option<CoreExpr> buildFile(IFile file) {
        EclipseWorkspaceFileSystem fs = new EclipseWorkspaceFileSystem(new EclipseWorkspaceFileSystemProvider(), this.getProject().getWorkspace(), null);
        final Path filePath = fs.getPath(file);

        // If the file exists, we'll show error markers for any parse errors
        // If the file doesn't exist, this was called in response to a deletion,
        // so we still want to "build" the project the file was part of.
        if(file.exists()) {
            deleteMarkers(file);
            IFileInfo fileInfo;
            try {
                fileInfo = EFS.getStore(file.getLocationURI()).fetchInfo();
            } catch(final CoreException e) {
                Activator.log(e.getStatus());
                return Option.none();
            }
            if(fileInfo.getLength() > Integer.MAX_VALUE) {
                // TODO Report error
                Activator.log("File too large to parse; files must be less than 2GB.");
                return Option.none();
            }

            // Check if the file parses first, if it doesn't even parse we can
            // skip the later steps
            List<BadExpr> parseProblems = SourceExprFromFile.forPath(filePath).getProblems();
            if(addMarkersForProblems(parseProblems))
                return Option.none();
            if(addDesugarProblemMarkers(CoreExprFromFile.forPath(filePath)))
                return Option.none();
        }

        // Return the bigger AST this file is part of - it'll be analyzed
        // further in the main build process
        Option<Path> projectRoot = CoreExprFactory.projectRootForPath(filePath);
        List<Path> langBundlePath = langBundleSearchPath();
        List<Path> paths = langBundlePath.append(projectRoot.toList());
        CoreExpr projectAst = CoreExprFactory.INSTANCE.loadFromDirectories(paths);
        return Option.some(projectAst);
	}

    public List<Path> langBundleSearchPath() {
        try {
            Bundle langBundle = Platform.getBundle("banjo.banjo-lang");
            if(langBundle == null)
                return List.nil();
            return List.single(Paths.get(FileLocator.resolve(langBundle.getResource("/")).toURI()));
        } catch(URISyntaxException e) {
            return List.nil();
        } catch(IOException e) {
            return List.nil();
        }
    }

    public boolean isBanjoSource(IResource resource) {
        return resource instanceof IFile && resource.getName().endsWith(".banjo") && !resource.getName().startsWith(".");
    }

    public boolean addDesugarProblemMarkers(CoreExpr projectAst) {
        List<BadExpr> problems = getDesugarProblems(projectAst);
        return addMarkersForProblems(problems);
    }

    public boolean addMarkersForProblems(List<BadExpr> problems) {
        boolean addedMarker = false;
		for(final BadExpr problem : problems) {
            SourceFileRange r = SourceFileRange.compactSet(problem.getSourceFileRanges()).iterator().next();
            addMarker(r, problem.getMessage(), IMarker.SEVERITY_ERROR);
            if(r.sourceFile instanceof EclipseWorkspacePath) {
                IFile file = ((EclipseWorkspacePath) r.sourceFile).getFile();
                if(file != null) {
                    addedMarker = true;
                }
            }
		}
        return addedMarker;
    }

    public List<BadExpr> getDesugarProblems(CoreExpr projectAst) throws Error {
        return callAsync(() -> CoreErrorGatherer.problems(projectAst), List.nil());
    }

    public <T> T callAsync(Callable<T> problemsCalculation, T fallback) throws Error {
        // If the job was already interrupted, always use the fallback value
        if(isInterrupted())
            return fallback;

        // Fire off the async job
        Future<T> desugarProblemsFuture = executor.submit(problemsCalculation);
        for(;;) {
            try {
                // Wait 100ms each time to see if the job finished
                return desugarProblemsFuture.get(100, TimeUnit.MILLISECONDS);
            } catch(TimeoutException te) {
                // If we are still waiting and we were requested to abort by
                // eclipse, cancel the job
                if(isInterrupted())
                    desugarProblemsFuture.cancel(true);

                // and return the fallback value
                return fallback;
            } catch(InterruptedException ie) {
                // If the thread was interrupted for any other reason, return
                // the fallback value
                return fallback;
            } catch(ExecutionException e) {
                // If an exception was thrown in the thread, throw an exception
                throw new Error(e);
            }
        }
    }

	public static boolean addParseProblemMarkers(final IFile file, final SourceExpr parseResult) {
		boolean haveParseProblems=false;
		for(final BadExpr problem : parseResult.getProblems()) {
			haveParseProblems = true;
			problem.getSourceFileRanges().forEach(r -> {
                addMarker(r, problem.getMessage(), IMarker.SEVERITY_ERROR);
			});
		}
		return haveParseProblems;
	}

	private void deleteMarkers(IFile file) {
		try {
			file.deleteMarkers(MARKER_TYPE, false, IResource.DEPTH_ZERO);
		} catch (final CoreException ce) {
			Activator.log(ce.getStatus());
		}
	}

    private Set<CoreExpr> buildSources(Set<IFile> banjoSourceFiles, int numberOfSourceFiles, final IProgressMonitor monitor) {
        monitor.beginTask("Checking syntax", numberOfSourceFiles);
        try {
            Set<CoreExpr> affectedProjectAsts = Set.empty(CoreExpr.coreExprOrd);
            for(IFile file : banjoSourceFiles) {
                if(monitor.isCanceled() || this.isInterrupted())
                    break;
                monitor.subTask("Checking syntax for " + file.getFullPath());
                try {
                    Option<CoreExpr> ast = buildFile(file);
                    if(ast.isSome())
                        affectedProjectAsts = affectedProjectAsts.insert(ast.some());
                } finally {
                    monitor.worked(1);
                }
            }
            return affectedProjectAsts;
        } finally {
            monitor.done();
        }
    }

    /**
     * Common code for incremental and full builds.
     * 
     * @param visitor
     *            Gathers the list of files to parse
     * @param monitor
     *            Progress monitor, used to give the user a clue about how much
     *            longer this might take
     */
    protected void projectBuild(BanjoBuilderVisitor visitor, final IProgressMonitor monitor) {
        monitor.beginTask("Building Banjo Project", 10100);
        try {
            visitor.collectSources();
            monitor.worked(100);
            Set<CoreExpr> projectAsts = buildSources(
                visitor.banjoSourceFiles,
                visitor.numberOfSourceFiles,
                new SubProgressMonitor(monitor, 5000));
            TreeMap<CoreExpr, P2<List<CoreExpr>, List<CoreExpr>>> testsAndExamples =
                TreeMap.treeMap(CoreExpr.coreExprOrd, projectAsts.toList().map(
                    (projectAst) -> P.p(projectAst, 
                        P.p(TestAndExampleGatherer.findTests(projectAst).toList(), TestAndExampleGatherer.findExamples(projectAst).toList()))));
            int totalTestsAndExamples = testsAndExamples.values().foldRight((a, b) -> a._1().length() + a._2().length() + b, 0);
            if(totalTestsAndExamples > 0) {
                int step = 5000 / totalTestsAndExamples;
                for(P2<CoreExpr, P2<List<CoreExpr>, List<CoreExpr>>> p : testsAndExamples) {
                    if(monitor.isCanceled() || this.isInterrupted())
                        return;
                    CoreExpr projectAst = p._1();
                    List<CoreExpr> tests = p._2()._1();
                    List<CoreExpr> examples = p._2()._2();
                    Environment env = Environment.forProjectAst(projectAst);
                    runTests(env, tests, monitor, step);
                    runTests(env, examples, monitor, step);
                }
            } else {
                monitor.worked(5000);
            }
        } catch (final CoreException e) {
            Activator.log(e.getStatus());
        } finally {
            monitor.done();
        }
    }

    /**
     * Try to give a useful indication of why an example or test wasn't true.
     * 
     * @param env
     * @param test
     * @param noscope
     * @return
     */
    public static String explainFailure(Environment env, CoreExpr test, CoreExpr noscope) {
        return explainFailure(env, test, noscope, TreeMap.empty(Ord.stringOrd));
    }

    public static String explainFailure(Environment env, CoreExpr test, CoreExpr noscope, TreeMap<String, CoreExpr> defs) {
        Value v = env.eval(test);
        if(v instanceof Fail) {
            return ((Fail) v).getMessage();
        }
        List<Value> trace = List.nil();
        Value andSlot = v.slot(trace, Operator.LOGICAL_AND.methodName);
        if(andSlot instanceof Fail) {
            if(andSlot instanceof SlotNotFound) {
                return "Expected a true or false value; got " + v;
            }
            return ((Fail) andSlot).getMessage();
        }
        return test.acceptVisitor(new BaseCoreExprVisitor<String>() {
            @Override
            public String fallback() {
                return "Expected a boolean expression, found " + noscope.toSource();
            }
            
            @Override
            public String badExpr(BadCoreExpr badExpr) {
                return badExpr.toString();
            }

            @Override
            public String identifier(Identifier identifier) {
                Option<CoreExpr> def = defs.get(identifier.id);
                if(def.isSome()) {
                    return def.some().acceptVisitor(this);
                }
                return this.fallback();
            }

            @Override
            public String call(Call call) {
                Value target = env.eval(call.target);
                if(target instanceof Fail) {
                    return call.target.toSource() + ": " + ((Fail) target).getMessage();
                }
                for(CoreExpr arg : call.args) {
                    Value argValue = env.eval(arg);
                    if(arg instanceof Fail)
                        return arg.toSource() + ": " + ((Fail) argValue).getMessage();
                }
                return call.target.acceptVisitor(new BaseCoreExprVisitor<String>() {
                    @Override
                    public String fallback() {
                        return "Result of "+call+" is not true";
                    }

                    @Override
                    public String projection(Projection projection) {
                        // Check for the common case of x == y
                        if(call.args.isSingle() && projection.projection.eql(Operator.EQ.methodNameKey)) {
                            Value lhs = env.eval(projection.object);
                            Value rhs = env.eval(call.args.head());
                            
                            return projection.object + " != " + call.args.head() + " since " + lhs + " != " + rhs;
                        } else {
                            return fallback();
                        }
                    }
                });
            }

            @Override
            public String badIdentifier(BadIdentifier badIdentifier) {
                return badIdentifier.toString();
            }

            @Override
            public String let(Let let) {
                List<P2<String, CoreExpr>> stringExprBindings = let.bindings.map(P2.map1_(Identifier::getId));
                List<P2<String, FreeExpression>> bindings1 = stringExprBindings.map(P2.map2_(FreeExpressionFactory::apply));
                Environment env2 = env.let(bindings1);
                return explainFailure(env2, let.body, noscope, defs.union(stringExprBindings));
            }

            @Override
            public String projection(Projection projection) {
                Value objectValue = env.eval(projection.object);
                if(objectValue instanceof Fail) {
                    return projection.object.toSource() + ": " + ((Fail) objectValue).getMessage();
                }
                if(projection.base) {
                    return this.fallback();
                }
                Environment env2 = env.projection(objectValue);
                return explainFailure(env2, projection.projection, noscope, TreeMap.empty(Ord.stringOrd));
            }
        });
    }

    public void runTests(Environment env, List<CoreExpr> tests, final IProgressMonitor monitor, int step) throws Error {
        for(CoreExpr test : tests) {
            if(monitor.isCanceled() || this.isInterrupted())
                return;
            CoreExpr noscope = TestAndExampleGatherer.stripScope(test);
            Set<SourceFileRange> ranges =
                SourceFileRange.compactSet(noscope.getSourceFileRanges()).filter(r -> r.getSourceFile() instanceof EclipseWorkspacePath);
            if(ranges.isEmpty()) {
                continue;
            }
            SourceFileRange r = ranges.iterator().next();
            boolean success = callAsync(() -> env.eval(test).isTrue(List.nil()), true);
            if(!success) {
                String reason = callAsync(() -> explainFailure(env, test, noscope), "Not true: " + noscope);
                addMarker(r, reason, IMarker.SEVERITY_ERROR);
            }
            monitor.worked(step);
        }
    }

    protected void fullBuild(final IProgressMonitor monitor) {
        projectBuild(new BanjoBuilderProjectVisitor(getProject()), monitor);
    }

    protected void incrementalBuild(IResourceDelta delta, IProgressMonitor monitor) throws CoreException {
        projectBuild(new BanjoBuilderDeltaVisitor(delta), monitor);
	}
}
