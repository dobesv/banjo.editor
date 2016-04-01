package banjo.builder;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
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
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.QualifiedName;

import banjo.editor.Activator;
import banjo.eval.environment.Environment;
import banjo.expr.BadExpr;
import banjo.expr.core.CoreErrorGatherer;
import banjo.expr.core.CoreExpr;
import banjo.expr.core.CoreExprFactory;
import banjo.expr.core.DefRefAnalyser;
import banjo.expr.core.TestAndExampleGatherer;
import banjo.expr.source.SourceExpr;
import banjo.expr.source.SourceExprFromFile;
import banjo.expr.util.FileRange;
import banjo.expr.util.ParserReader;
import banjo.expr.util.SourceFileRange;
import fj.data.List;
import fj.data.Set;

public class BanjoBuilder extends IncrementalProjectBuilder {
	class BanjoBuilderDeltaVisitor implements IResourceDeltaVisitor {
		final IProgressMonitor monitor;
		
		public BanjoBuilderDeltaVisitor(IProgressMonitor monitor) {
			super();
			this.monitor = monitor;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see org.eclipse.core.resources.IResourceDeltaVisitor#visit(org.eclipse.core.resources.IResourceDelta)
		 */
		@Override
		public boolean visit(IResourceDelta delta) throws CoreException {
			final IResource resource = delta.getResource();
			switch (delta.getKind()) {
			case IResourceDelta.ADDED:
				// handle added resource
				build(resource, monitor);
				break;
			case IResourceDelta.REMOVED:
				// handle removed resource
				break;
			case IResourceDelta.CHANGED:
				// handle changed resource
				build(resource, monitor);
				break;
			}
			//return true to continue visiting children.
			return true;
		}
	}

	class BanjoBuilderResourceVisitor implements IResourceVisitor {
		private IProgressMonitor monitor;

		public BanjoBuilderResourceVisitor(IProgressMonitor monitor) {
			this.monitor = monitor;
		}

		@Override
		public boolean visit(IResource resource) {
			build(resource, monitor);
			//return true to continue visiting children.
			return true;
		}
	}

	public static final String BUILDER_ID = "banjo.editor.banjoBuilder";
	private static final String MARKER_TYPE = IMarker.PROBLEM;
	public static final QualifiedName AST_CACHE_PROPERTY = new QualifiedName(Activator.PLUGIN_ID, "astCache");
    public static ExecutorService executor = Executors.newCachedThreadPool();

	private static void addMarker(IFile file, String message, FileRange range, int severity) {
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
		getProject().deleteMarkers(MARKER_TYPE, true, IResource.DEPTH_INFINITE);
		getProject().accept(new IResourceVisitor() {

			@Override
			public boolean visit(IResource resource) throws CoreException {
				if(resource instanceof IFile) {
					clearAstCache((IFile)resource);
				}
				return true;
			}
		});
	}

	void build(IResource resource, IProgressMonitor monitor) {
		if (resource instanceof IFile && resource.getName().endsWith(".banjo")) {
			final IFile file = (IFile) resource;
			deleteMarkers(file);
			clearAstCache(file);
			IFileInfo fileInfo;
			try {
				fileInfo = EFS.getStore(file.getLocationURI()).fetchInfo();
			} catch (final CoreException e) {
				Activator.log(e.getStatus());
				return;
			}
			if(fileInfo.getLength() > Integer.MAX_VALUE) {
				// TODO Report error
				Activator.log("File too large to parse; files must be less than 2GB.");
				return;
			}

            EclipseWorkspaceFileSystem fs = new EclipseWorkspaceFileSystem(new EclipseWorkspaceFileSystemProvider(), this.getProject().getWorkspace(), null);
            // Load the whole project surrounding that file
            final Path filePath = fs.getPath(file);

            // Check if the file parses first, if it doesn't even parse we can
            // skip the later steps
            List<BadExpr> parseProblems = SourceExprFromFile.forPath(filePath).getProblems();
            if(!parseProblems.isEmpty()) {
                addMarkersForProblems(file, parseProblems);
            } else {
                List<Path> paths = CoreExprFactory.projectSourcePathsForFile(filePath);
                CoreExpr projectAst = CoreExprFactory.INSTANCE.loadFromDirectories(paths);
                addMarkers(file, projectAst);
            }
		}
	}

    public void addMarkers(final IFile file, final CoreExpr projectAst) {
		try {
            file.setSessionProperty(AST_CACHE_PROPERTY, projectAst);
		} catch (final CoreException e) {
			Activator.log(e.getStatus());
		}

        if(addDesugarProblemMarkers(file, projectAst))
            return;

        // TODO Def/ref analysis needs type information now ... only the
        // full-blown type directed analysis will work now
        // addDefRefProblemMarkers(file, projectAst);

        if(addExampleProblemMarkers(file, projectAst))
            return;

        if(addUnitTestProblemMarkers(file, projectAst))
            return;

	}

    public boolean addDefRefProblemMarkers(final IFile file, CoreExpr projectAst) {
        final List<BadExpr> problems = callAsync(() -> DefRefAnalyser.problems(projectAst), List.nil());
        return addMarkersForProblems(file, problems);
    }
    public boolean addDesugarProblemMarkers(final IFile file, CoreExpr projectAst) {
        List<BadExpr> problems = getDesugarProblems(projectAst);
        return addMarkersForProblems(file, problems);
    }

    public boolean addMarkersForProblems(final IFile file, List<BadExpr> problems) {
        final Path fileWeAreMarking = EclipseWorkspacePath.of(file, null);
        boolean addedMarker = false;
		for(final BadExpr problem : problems) {
            Set<SourceFileRange> rangesInTargetFile = problem.getSourceFileRanges().filter(r -> r.getSourceFile().equals(fileWeAreMarking));
            if(rangesInTargetFile.isEmpty())
                continue;
            SourceFileRange r = rangesInTargetFile.iterator().next();
            addMarker(file, problem.getMessage(), r.getFileRange(), IMarker.SEVERITY_ERROR);
            addedMarker = true;
		}
        return addedMarker;
    }

    public boolean addExampleProblemMarkers(final IFile file, CoreExpr projectAst) {
        Callable<List<CoreExpr>> gatherer = () -> TestAndExampleGatherer.findExamples(projectAst);
        return addTestFailureMarkers(file, gatherer);
    }

    public boolean addUnitTestProblemMarkers(final IFile file, CoreExpr projectAst) {
        Callable<List<CoreExpr>> gatherer = () -> TestAndExampleGatherer.findTests(projectAst);
        return addTestFailureMarkers(file, gatherer);
    }

    public boolean addTestFailureMarkers(final IFile file, Callable<List<CoreExpr>> gatherer) throws Error {
        List<CoreExpr> tests = callAsync(gatherer, List.nil());
        if(tests.isEmpty())
            return false;
        final Path fileWeAreMarking = EclipseWorkspacePath.of(file, null);
        Environment environment = Environment.forSourcePath(fileWeAreMarking);
        boolean failedTest = false;
        for(CoreExpr e : tests) {
            CoreExpr noscope = TestAndExampleGatherer.stripScope(e);
            Set<SourceFileRange> rangesInTargetFile = noscope.getSourceFileRanges().filter(r -> r.getSourceFile().equals(fileWeAreMarking));
            if(rangesInTargetFile.isEmpty())
                continue;
            SourceFileRange r = rangesInTargetFile.iterator().next();
            boolean success = callAsync(() -> environment.eval(e).isTruthy(), true);
            if(!success) {
                failedTest = true;
                addMarker(file, "Failed: " + noscope.toSource(), r.getFileRange(), IMarker.SEVERITY_ERROR);
            } else {
                addMarker(file, "Passed: " + noscope.toSource(), r.getFileRange(), IMarker.SEVERITY_INFO);
            }
        }
        return failedTest;
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
				addMarker(file, problem.getMessage(), r.getFileRange(), IMarker.SEVERITY_ERROR);
			});
		}
		return haveParseProblems;
	}


	public static void clearAstCache(IFile file) {
		try {
			file.setSessionProperty(AST_CACHE_PROPERTY, null);
		} catch (final CoreException e) {
			Activator.log(e.getStatus());
		}
	}

	private void deleteMarkers(IFile file) {
		try {
			file.deleteMarkers(MARKER_TYPE, false, IResource.DEPTH_ZERO);
		} catch (final CoreException ce) {
			Activator.log(ce.getStatus());
		}
	}

	protected void fullBuild(final IProgressMonitor monitor) {
		try {
			clean(monitor);
			getProject().accept(new BanjoBuilderResourceVisitor(monitor));
		} catch (final CoreException e) {
			Activator.log(e.getStatus());
		}
	}

	protected void incrementalBuild(IResourceDelta delta, IProgressMonitor monitor) throws CoreException {
		delta.accept(new BanjoBuilderDeltaVisitor(monitor));
	}
}
