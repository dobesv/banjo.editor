package banjo.builder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.util.Map;

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
import banjo.eval.ProjectLoader;
import banjo.eval.environment.Environment;
import banjo.expr.BadExpr;
import banjo.expr.core.CoreErrorGatherer;
import banjo.expr.core.CoreExpr;
import banjo.expr.core.CoreExprFactory;
import banjo.expr.core.CoreExprFactory.DesugarResult;
import banjo.expr.core.DefRefAnalyser;
import banjo.expr.core.ObjectLiteral;
import banjo.expr.source.SourceExpr;
import banjo.expr.source.SourceExprFactory;
import banjo.expr.token.Identifier;
import banjo.expr.util.FileRange;
import banjo.expr.util.ParserReader;
import fj.P;
import fj.P2;
import fj.data.List;

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
	
//	class EclipseBanjoProjectLoader extends ProjectLoader {
//		@Override
//		public CoreExpr loadSourceCode(Path path) {
//			if(path.isAbsolute())
//				return super.loadSourceCode(path);
//			IResource f = getResource(path);
//			if(f == null || !f.exists())
//		    	return new BadCoreExpr(new SourceFileRange(path.toString(), FileRange.EMPTY), "Error reading file '"+path+"': File not found");
//			try {
//	            CoreExpr cached = (CoreExpr)f.getSessionProperty(AST_CACHE_PROPERTY);
//	            if(cached != null) {
//	            	return cached;
//	            }
//			    cached = super.loadSourceCode(path);
//			    f.setSessionProperty(AST_CACHE_PROPERTY, cached);
//			    return cached;
//            } catch (CoreException e) {
//	            e.printStackTrace();
//		    	return new BadCoreExpr(new SourceFileRange(path.toString(), FileRange.EMPTY), "Error reading file '"+path+"': "+e);
//            }
//		}
//
//		protected IResource getResource(Path path) {
//			return getProject().findMember(path.toString());
//        }
//
//		@Override
//		protected Reader openFile(Path path) throws FileNotFoundException {
//			if(path.isAbsolute())
//				return super.openFile(path);
//			IResource res = getResource(path);
//			if(res == null || !res.exists())
//				throw new FileNotFoundException();
//			if(res.getType() != IResource.FILE)
//				throw new FileNotFoundException("Path is not a file");
//			try {
//				IFile f = (IFile)res;
//	            return new InputStreamReader(f.getContents(), f.getCharset());
//            } catch (UnsupportedEncodingException e) {
//            	throw new Error(e);
//            } catch (CoreException e) {
//            	throw new Error(e);
//            }
//		}
//		@Override
//		public Stream<Path> listFilesInFolder(Path path) throws IOException {
//			if(path.isAbsolute())
//				return super.listFilesInFolder(path);
//			IResource f = getResource(path);
//			if(f == null || (f.getType() != IResource.PROJECT && f.getType() != IResource.FOLDER)) {
//				return Stream.empty();
//			}
//			try {
//	            return Stream.of(((IContainer)f).members(IContainer.INCLUDE_HIDDEN)).map(res -> path.resolve(res.getName()));
//            } catch (CoreException e) {
//            	throw new Error(e);
//            }
//		}
//
//		@Override
//		protected boolean fileExists(Path path) {
//			if(path.isAbsolute())
//				return super.fileExists(path);
//		    final IResource res = getResource(path);
//			return res != null && res.exists();
//		}
//
//		@Override
//		protected long fileSize(Path path) throws IOException {
//			if(path.isAbsolute())
//				return super.fileSize(path);
//		    final IResource res = getResource(path);
//		    if(res.getType() != IResource.FILE)
//		    	return 0;
//		    try {
//	            return EFS.getStore(((IFile)res).getLocationURI()).fetchInfo().getLength();
//            } catch (CoreException e) {
//	            e.printStackTrace();
//	            Activator.log(e);
//	            return 0;
//            }
//		}
//
//		@Override
//		protected boolean isRegularFile(Path path) {
//			if(path.isAbsolute())
//				return super.isRegularFile(path);
//		    final IResource f = getResource(path);
//			return f != null && f.exists() && f.getType() == IResource.FILE;
//		}
//
//		@Override
//		protected boolean isDirectory(Path path) {
//			if(path.isAbsolute())
//				return super.isDirectory(path);
//			final IResource f = getResource(path);
//		    return f != null && f.exists() && f.getType() == IResource.FOLDER;
//		}
//
//	}
//
//	public final EclipseBanjoProjectLoader loader = new EclipseBanjoProjectLoader();

	public static final String BUILDER_ID = "banjo.editor.banjoBuilder";
	private static final String MARKER_TYPE = IMarker.PROBLEM;
	public static final QualifiedName AST_CACHE_PROPERTY = new QualifiedName(Activator.PLUGIN_ID, "astCache");

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
			Reader reader;
			try {
				reader = new BufferedReader(new InputStreamReader(file.getContents(true), file.getCharset()));
			} catch (final UnsupportedEncodingException e) {
				Activator.log(e);
				return;
			} catch (final CoreException e) {
				Activator.log(e.getStatus());
				return;
			}
			if(fileInfo.getLength() > Integer.MAX_VALUE) {
				// TODO Report error
				Activator.log("File too large to parse; files must be less than 2GB.");
				return;
			}

			try {
				EclipseWorkspaceFileSystem fs = new EclipseWorkspaceFileSystem(new EclipseWorkspaceFileSystemProvider(), this.getProject().getWorkspace(), null);
				final Path filePath = fs.getPath(file);
				final ParserReader in = new ParserReader(reader, (int)fileInfo.getLength());
				final SourceExprFactory parser = new SourceExprFactory(filePath);
				final SourceExpr parseResult = parser.parse(in);
				final CoreExprFactory desugarer = new CoreExprFactory();
				final DesugarResult<CoreExpr> desugarResult = desugarer.desugar(parseResult);
				
				
				ProjectLoader loader = new ProjectLoader();
				List<P2<Identifier, CoreExpr>> bindings = loader.loadLocalAndLibraryBindings(filePath).cons(
						P.p(new Identifier(Environment.JAVA_RUNTIME_ID), new ObjectLiteral())
						);

				final CoreExpr ast = desugarResult.getValue();
				addMarkers(file, parseResult, ast, bindings);
			} catch (final IOException e) {
				Activator.log("Error while reading "+file.getName(), e);
				return;
			} finally {
				try {
					reader.close();
				} catch (final IOException e) {
					Activator.log("Error while closing "+file.getName(), e);
				}
			}
		}
	}

	public static boolean addMarkers(final IFile file, final SourceExpr parseTree, final CoreExpr ast, List<P2<Identifier, CoreExpr>> bindings) {
		try {
			file.setSessionProperty(AST_CACHE_PROPERTY, ast);
		} catch (final CoreException e) {
			Activator.log(e.getStatus());
		}
		return addParseProblemMarkers(file, parseTree) ||
				addDesugarProblemMarkers(file, ast, bindings);
	}

	public static boolean addDesugarProblemMarkers(final IFile file, CoreExpr ast, List<P2<Identifier, CoreExpr>> bindings) {
		final List<BadExpr> desugarProblems = CoreErrorGatherer.problems(ast);
		final List<BadExpr> defRefProblems = DefRefAnalyser.problems(ast, bindings);
		final List<BadExpr> problems = desugarProblems.append(defRefProblems);
		final Path fileWeAreMarking = EclipseWorkspacePath.of(file, null);
		for(final BadExpr problem : problems) {
			problem.getSourceFileRanges().forEach(r -> {
				final Path sourceFile = r.getSourceFile();
				if(sourceFile.equals(fileWeAreMarking))
					addMarker(file, problem.getMessage(), r.getFileRange(), IMarker.SEVERITY_ERROR);
			});
		}
		return problems.isNotEmpty();
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
