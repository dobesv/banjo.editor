package banjo.builder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
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

import banjo.desugar.SourceExprDesugarer;
import banjo.desugar.SourceExprDesugarer.DesugarResult;
import banjo.dom.BadExpr;
import banjo.dom.core.CoreExpr;
import banjo.dom.source.SourceExpr;
import banjo.editor.Activator;
import banjo.parser.SourceCodeParser;
import banjo.parser.util.FileRange;
import banjo.parser.util.ParserReader;

public class BanjoBuilder extends IncrementalProjectBuilder {
	class BanjoBuilderDeltaVisitor implements IResourceDeltaVisitor {
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
				build(resource);
				break;
			case IResourceDelta.REMOVED:
				// handle removed resource
				break;
			case IResourceDelta.CHANGED:
				// handle changed resource
				build(resource);
				break;
			}
			//return true to continue visiting children.
			return true;
		}
	}

	class BanjoBuilderResourceVisitor implements IResourceVisitor {
		@Override
		public boolean visit(IResource resource) {
			build(resource);
			//return true to continue visiting children.
			return true;
		}
	}

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
		final ParserReader in = new ParserReader(new InputStreamReader(file.getContents(), file.getCharset()), "", (int) fileLength);
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

	void build(IResource resource) {
		if (resource instanceof IFile && resource.getName().endsWith(".banjo")) {
			final IFile file = (IFile) resource;
			deleteMarkers(file);
			clearAstCache(file);
			final SourceCodeParser parser = new SourceCodeParser();
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
				final ParserReader in = new ParserReader(reader, file.getName(), (int)fileInfo.getLength());
				final SourceExpr parseResult = parser.parse(in);
				final SourceExprDesugarer desugarer = new SourceExprDesugarer();
				final DesugarResult<CoreExpr> desugarResult = desugarer.desugar(parseResult);
				addMarkers(file, parseResult, desugarResult);
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

	public static boolean addMarkers(final IFile file, final SourceExpr parseResult, final DesugarResult<CoreExpr> desugarResult) {
		try {
			file.setSessionProperty(AST_CACHE_PROPERTY, desugarResult);
		} catch (final CoreException e) {
			Activator.log(e.getStatus());
		}
		return addParseProblemMarkers(file, parseResult) ||
				addDesugarProblemMarkers(file, parseResult, desugarResult);
	}

	public static boolean addDesugarProblemMarkers(final IFile file,
			final SourceExpr parseResult,
			final DesugarResult<CoreExpr> desugarResult) {
		boolean haveDesugarProblems=false;
		for(final BadExpr problem : desugarResult.getProblems()) {
			haveDesugarProblems = true;
			problem.getSourceFileRanges().forEach(r -> {
				addMarker(file, problem.getMessage(), r.getFileRange(), IMarker.SEVERITY_ERROR);
			});
		}
		return haveDesugarProblems;
	}

	public static boolean addParseProblemMarkers(final IFile file,
			final SourceExpr parseResult) {
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
			getProject().accept(new BanjoBuilderResourceVisitor());
		} catch (final CoreException e) {
			Activator.log(e.getStatus());
		}
	}

	protected void incrementalBuild(IResourceDelta delta, IProgressMonitor monitor) throws CoreException {
		delta.accept(new BanjoBuilderDeltaVisitor());
	}
}
