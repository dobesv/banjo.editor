package banjo.builder;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Map;

import javax.xml.parsers.SAXParserFactory;

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
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import banjo.desugar.BanjoDesugarer;
import banjo.dom.SourceExpr;
import banjo.editor.Activator;
import banjo.parser.BanjoParser;
import banjo.parser.errors.BanjoParseException;
import banjo.parser.util.ParserReader;

public class BanjoBuilder extends IncrementalProjectBuilder {

	class BanjoErrorCheckingDeltaVisitor implements IResourceDeltaVisitor {
		/*
		 * (non-Javadoc)
		 * 
		 * @see org.eclipse.core.resources.IResourceDeltaVisitor#visit(org.eclipse.core.resources.IResourceDelta)
		 */
		public boolean visit(IResourceDelta delta) throws CoreException {
			IResource resource = delta.getResource();
			switch (delta.getKind()) {
			case IResourceDelta.ADDED:
				// handle added resource
				checkForErrors(resource);
				break;
			case IResourceDelta.REMOVED:
				// handle removed resource
				break;
			case IResourceDelta.CHANGED:
				// handle changed resource
				checkForErrors(resource);
				break;
			}
			//return true to continue visiting children.
			return true;
		}
	}

	class BanjoErrorCheckingResourceVisitor implements IResourceVisitor {
		public boolean visit(IResource resource) {
			checkForErrors(resource);
			//return true to continue visiting children.
			return true;
		}
	}

	public static final String BUILDER_ID = "banjo.editor.banjoBuilder";
	private static final String MARKER_TYPE = IMarker.PROBLEM;
	
	private void addMarker(IFile file, BanjoParseException err) {
		try {
			IMarker marker = file.createMarker(MARKER_TYPE);
			marker.setAttribute(IMarker.MESSAGE, err.getMessage());
			marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
			
			marker.setAttribute(IMarker.LINE_NUMBER, err.getStartLine());
			marker.setAttribute(IMarker.CHAR_START, err.getStartOffset());
			marker.setAttribute(IMarker.CHAR_END, err.getEndOffset());
		} catch (CoreException e) {
			Activator.log(e.getStatus());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.internal.events.InternalBuilder#build(int,
	 *      java.util.Map, org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected IProject[] build(int kind, Map args, IProgressMonitor monitor)
			throws CoreException {
		if (kind == FULL_BUILD) {
			fullBuild(monitor);
		} else {
			IResourceDelta delta = getDelta(getProject());
			if (delta == null) {
				fullBuild(monitor);
			} else {
				incrementalBuild(delta, monitor);
			}
		}
		return null;
	}

	protected void clean(IProgressMonitor monitor) throws CoreException {
		// delete markers set and files created
		getProject().deleteMarkers(MARKER_TYPE, true, IResource.DEPTH_INFINITE);
	}

	void checkForErrors(IResource resource) {
		if (resource instanceof IFile && resource.getName().endsWith(".banjo")) {
			IFile file = (IFile) resource;
			deleteMarkers(file);
			BanjoParser parser = new BanjoParser();
			IFileInfo fileInfo;
			try {
				fileInfo = EFS.getStore(file.getLocationURI()).fetchInfo();
			} catch (CoreException e) {
				Activator.log(e.getStatus());
				return;
			}
			InputStreamReader reader;
			try {
				reader = new InputStreamReader(file.getContents(true), file.getCharset());
			} catch (UnsupportedEncodingException e) {
				Activator.log(e);
				return;
			} catch (CoreException e) {
				Activator.log(e.getStatus());
				return;
			}
			if(fileInfo.getLength() > Integer.MAX_VALUE) {
				// TODO Report error 
				Activator.log("File too large to parse; files must be less than 2GB.");
				return;
			}
			
			try {
				SourceExpr sourceExpr = parser.parse(new ParserReader(reader, file.getName(), (int)fileInfo.getLength()));
				Collection<BanjoParseException> errors = parser.getErrors();
				if(errors.isEmpty()) {
					BanjoDesugarer desugarer = new BanjoDesugarer();
					desugarer.desugar(sourceExpr);
					errors = desugarer.getErrors();
				}
				for(BanjoParseException error : errors) {
					addMarker(file, error);
				}
			} catch (IOException e) {
				Activator.log("Error while reading "+file.getName(), e);
				return;
			}
		}
	}


	private void deleteMarkers(IFile file) {
		try {
			file.deleteMarkers(MARKER_TYPE, false, IResource.DEPTH_ZERO);
		} catch (CoreException ce) {
		}
	}

	protected void fullBuild(final IProgressMonitor monitor)
			throws CoreException {
		try {
			getProject().accept(new BanjoErrorCheckingResourceVisitor());
		} catch (CoreException e) {
		}
	}

	protected void incrementalBuild(IResourceDelta delta,
			IProgressMonitor monitor) throws CoreException {
		delta.accept(new BanjoErrorCheckingDeltaVisitor());
	}
}