package banjo.builder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Iterator;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;

public class EclipseWorkspacePath implements Path {
	public final EclipseWorkspaceFileSystem fileSystem;
	public final IPath path;
	
	public EclipseWorkspacePath(EclipseWorkspaceFileSystem fileSystem, IPath path) {
		super();
		this.fileSystem = fileSystem;
		this.path = path;
	}

	public EclipseWorkspacePath of(IPath path) {
		return new EclipseWorkspacePath(fileSystem, path);
	}
	@Override
	public FileSystem getFileSystem() {
		return fileSystem;
	}

	@Override
	public boolean isAbsolute() {
		return path.isAbsolute();
	}

	@Override
	public Path getRoot() {
		if(path.isRoot())
			return this;
		return of(path.uptoSegment(0).makeAbsolute());
	}

	@Override
	public Path getFileName() {
		return of(path.makeRelativeTo(path.makeRelativeTo(path.removeLastSegments(1))));
	}

	@Override
	public Path getParent() {
		if(path.segmentCount() == 0)
			return null;
		return of(path.removeLastSegments(1));
	}

	@Override
	public int getNameCount() {
		return path.segmentCount();
	}

	@Override
	public Path getName(int index) {
		return of(path.removeFirstSegments(index).uptoSegment(1));
	}

	@Override
	public Path subpath(int beginIndex, int endIndex) {
		return of(path.removeFirstSegments(beginIndex).uptoSegment(endIndex-beginIndex));
	}

	@Override
	public boolean startsWith(Path other) {
		if(!(other instanceof EclipseWorkspacePath))
			return false;
		EclipseWorkspacePath p = (EclipseWorkspacePath) other;
		return p.path.isPrefixOf(path);
	}

	@Override
	public boolean startsWith(String other) {
		return path.uptoSegment(0).append(other).isPrefixOf(path);
	}

	@Override
	public boolean endsWith(Path other) {
		if(!(other instanceof EclipseWorkspacePath))
			return false;
		EclipseWorkspacePath p = (EclipseWorkspacePath) other;
		if(p.path.segmentCount() > path.segmentCount())
			return false;
		return path.removeFirstSegments(path.segmentCount() - p.path.segmentCount()).equals(p.path);
	}

	@Override
	public boolean endsWith(String other) {
		IPath p2 = path.uptoSegment(0).append(other);
		if(p2.segmentCount() > path.segmentCount())
			return false;
		return path.removeFirstSegments(path.segmentCount() - p2.segmentCount()).equals(p2);
	}

	@Override
	public Path normalize() {
		return this;
	}

	@Override
	public Path resolve(Path other) {
		if(other instanceof EclipseWorkspacePath) {
			return of(path.append(((EclipseWorkspacePath) other).path));
		} else {
			IPath result = path;
			for(Path part : other) {
				result = result.append(part.toString());
			}
			return of(result);
		}
	}

	@Override
	public Path resolve(String other) {
		return of(path.append(other));
	}

	@Override
	public Path resolveSibling(Path other) {
		return getParent().resolve(other);
	}

	@Override
	public Path resolveSibling(String other) {
		return getParent().resolve(other);
	}

	@Override
	public Path relativize(Path other) {
		throw new IllegalArgumentException("relativize not supported");
	}

	@Override
	public URI toUri() {
		try {
            return new URI("eclipse", null, path.makeAbsolute().toString(), null);
		} catch (URISyntaxException e) {
			throw new Error(e);
		}
	}

	@Override
	public Path toAbsolutePath() {
		if(path.isAbsolute())
			return this;
		return of(path.makeAbsolute());
	}

	@Override
	public Path toRealPath(LinkOption... options) throws IOException {
		return toAbsolutePath();
	}

	@Override
	public File toFile() {
		return path.toFile();
	}

	@Override
	public WatchKey register(WatchService watcher, Kind<?>[] events, Modifier... modifiers) throws IOException {
		return toFile().toPath().register(watcher, events, modifiers);
	}

	@Override
	public WatchKey register(WatchService watcher, Kind<?>... events) throws IOException {
		return toFile().toPath().register(watcher, events);
	}

	@Override
	public Iterator<Path> iterator() {
		return new Iterator<Path>() {
			int i = 0;
			
			@Override
			public boolean hasNext() {
				return i < path.segmentCount();
			}
			
			@Override
			public Path next() {
				return getName(i++);
			}
		};
	}

	@Override
	public int compareTo(Path other) {
		return toString().compareTo(other.toString());
	}
	
	@Override
	public boolean equals(Object other) {
		if(!(other instanceof EclipseWorkspacePath))
			return false;
		return path.equals(((EclipseWorkspacePath)other).path);
	}

	/**
	 * Get an existing resource.  Returns null if the resource doesn't
	 * exist.  Otherwise, the resource type is determined by what is
	 * found at that path.
	 * @return
	 */
	public IResource getResource() {
		return fileSystem.workspace.getRoot().findMember(path);
	}

	/**
	 * Get an IPath for this file path.  If this path refers to a project that doesn't exist or is not
	 * located on the local filesystem, returns null.  Otherwise, the project is used to calculate the
	 * path.  The target file/folder need not exist.
	 */
	public IPath toIPath() {
		if(path.segmentCount() == 0) {
			return fileSystem.workspace.getRoot().getLocation();
		}
		if(path.segmentCount() == 1) {
			return fileSystem.workspace.getRoot().getProject(path.segment(0)).getLocation();
		}
		return getFile().getLocation();
	}
	
	/**
	 * Get a filesystem path.  If this path is in a project that doesn't exist or is not
	 * located on the local filesystem, returns null.  Otherwise, the target file/folder
	 * need not exist.
	 */
	public Path toFileSystemPath() {
		IPath ipath = toIPath();
		if(ipath == null)
			return null;
		File file = ipath.toFile();
		return file.toPath();
	}

	/**
     * Get a container handle without checking if anything actually exists.
     */
	public IContainer getContainer() {
        IWorkspaceRoot root = fileSystem.workspace.getRoot();
        if(path.segmentCount() == 0) {
            return root;
        }
        if(path.segmentCount() == 1) {
            return root.getProject(path.segment(0));
        }
        return root.getFolder(path);
	}

    /**
     * Get a folder handle without checking if anything actually exists. Returns
     * null if the path has fewer than 2 segments (this cannot be used to refer
     * to the workspace or a project).
     */
    public IFolder getFolder() {
        IWorkspaceRoot root = fileSystem.workspace.getRoot();
        if(path.segmentCount() <= 1) {
            return null;
        }
        return root.getFolder(path);
    }

	/**
	 * Get a file handle without checking if anything actually exists.
	 */
	public IFile getFile() {
		return fileSystem.workspace.getRoot().getFile(path);
	}
	
    @Override
    public String toString() {
        return toUri().toString();
    }
	
	public static EclipseWorkspacePath of(IResource res, IProgressMonitor monitor) {
		return new EclipseWorkspaceFileSystem(new EclipseWorkspaceFileSystemProvider(), res.getWorkspace(), monitor).getPath(res);
	}
}
