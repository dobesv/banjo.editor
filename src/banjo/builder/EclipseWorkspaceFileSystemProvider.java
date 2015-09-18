package banjo.builder;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

public class EclipseWorkspaceFileSystemProvider extends FileSystemProvider {
	EclipseWorkspaceFileSystem fs;
	
	@Override
	public String getScheme() {
		return "eclipse";
	}

	@Override
	public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
		// Currently we are ignoring all uri components except the scheme
		if(fs != null)
			throw new FileSystemAlreadyExistsException();
		IWorkspace workspace = (IWorkspace) env.get("workspace");
		IProgressMonitor progress = (IProgressMonitor) env.get("progress");
		fs = new EclipseWorkspaceFileSystem(this, workspace, progress);
		return fs;
	}

	@Override
	public FileSystem getFileSystem(URI uri) {
		if(fs != null)
			return fs;
		throw new FileSystemNotFoundException();
	}

	@Override
	public Path getPath(URI uri) {
		return getFileSystem(uri).getPath(uri.getPath());
	}

	@Override
	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options,
			FileAttribute<?>... attrs) throws IOException {
		EclipseWorkspacePath p = (EclipseWorkspacePath)path;
		
		// TODO This isn't going to work for non-local files 
		Path pp = p.toFileSystemPath();
		if(pp == null) {
			throw new FileNotFoundException("File is is a non-existant or non-local project");
		}
		return Files.newByteChannel(pp, options, attrs);
	}

	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
		EclipseWorkspacePath p = (EclipseWorkspacePath)dir;
		try {
			Stream<IResource> a = Stream.of(p.getFolder().members());
			Stream<Path> paths = a.map(res -> p.of(res.getFullPath()));
			return new DirectoryStream<Path>() {
				@Override
				public Iterator<Path> iterator() {
					return paths.iterator();
				}

				@Override
				public void close() throws IOException {
				}
			};
		} catch (CoreException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
		EclipseWorkspacePath p = (EclipseWorkspacePath)dir;
		try {
			p.getFolder().create(true, true, p.fileSystem.progress);
		} catch (CoreException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void delete(Path path) throws IOException {
		EclipseWorkspacePath p = (EclipseWorkspacePath)path;
		IResource res = p.getResource();
		if(res != null) {
			try {
				res.delete(true, p.fileSystem.progress);
			} catch (CoreException e) {
				throw new IOException(e);
			}
		}
	}

	@Override
	public void copy(Path source, Path target, CopyOption... options) throws IOException {
		EclipseWorkspacePath s = (EclipseWorkspacePath)source;
		EclipseWorkspacePath t = (EclipseWorkspacePath)target;
		if(s.fileSystem != t.fileSystem)
			throw new IOException();
		IResource res = s.getResource();
		if(res == null) {
			throw new FileNotFoundException();
		}
		try {
			res.copy(t.path, true, s.fileSystem.progress);
		} catch (CoreException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void move(Path source, Path target, CopyOption... options) throws IOException {
		EclipseWorkspacePath s = (EclipseWorkspacePath)source;
		EclipseWorkspacePath t = (EclipseWorkspacePath)target;
		if(s.fileSystem != t.fileSystem)
			throw new IOException();
		IResource res = s.getResource();
		if(res == null) {
			throw new FileNotFoundException();
		}
		try {
			res.move(t.path, true, s.fileSystem.progress);
		} catch (CoreException e) {
			throw new IOException(e);
		}
	}

	@Override
	public boolean isSameFile(Path path, Path path2) throws IOException {
		return path.equals(path2);
	}

	@Override
	public boolean isHidden(Path path) throws IOException {
		return false;
	}

	@Override
	public FileStore getFileStore(Path path) throws IOException {
		Path pp = ((EclipseWorkspacePath)path).toFileSystemPath();
		if(pp == null) {
			throw new FileNotFoundException("File is is a non-existant or non-local project");
		}
		return Files.getFileStore(pp);
	}

	@Override
	public void checkAccess(Path path, AccessMode... modes) throws IOException {
		EclipseWorkspacePath p = (EclipseWorkspacePath)path;
		Path pp = p.toFileSystemPath();
		if(pp == null) {
			throw new FileNotFoundException("File is is a non-existant or non-local project");
		}
		if(modes.length == 0) {
			if(!Files.exists(pp))
				throw new FileNotFoundException();
		}
		for(AccessMode mode : modes) {
			switch(mode) {
			case EXECUTE:
				if(!Files.isExecutable(pp))
					throw new AccessDeniedException(path.toString());
				break;
			case READ:
				if(!Files.isReadable(pp))
					throw new AccessDeniedException(path.toString());
				break;
			case WRITE:
				if(!Files.isWritable(pp))
					throw new AccessDeniedException(path.toString());
				break;
			}
		}
	}

	@Override
	public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
		EclipseWorkspacePath p = (EclipseWorkspacePath)path;
		Path pp = p.toFileSystemPath();
		return Files.getFileAttributeView(pp, type, options);
	}

	@Override
	public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
			throws IOException {
		EclipseWorkspacePath p = (EclipseWorkspacePath)path;
		Path pp = p.toFileSystemPath();
		if(pp == null) {
			throw new FileNotFoundException("File is is a non-existant or non-local project");
		}
		return Files.readAttributes(pp, type, options);
	}

	@Override
	public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options)
			throws IOException {
		EclipseWorkspacePath p = (EclipseWorkspacePath)path;
		Path pp = p.toFileSystemPath();
		if(pp == null) {
			throw new FileNotFoundException("File is is a non-existant or non-local project");
		}
		return Files.readAttributes(pp, attributes, options);
	}

	@Override
	public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
		EclipseWorkspacePath p = (EclipseWorkspacePath)path;
		Path pp = p.toFileSystemPath();
		if(pp == null) {
			throw new FileNotFoundException("File is is a non-existant or non-local project");
		}
		Files.setAttribute(pp, attribute, value, options);
	}
	
}