package banjo.builder;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;

public class EclipseWorkspaceFileSystem extends FileSystem {
	private final EclipseWorkspacePath root = new EclipseWorkspacePath(this, new org.eclipse.core.runtime.Path("/"));
	private final List<Path> rootDirectories = Arrays.asList(root);
	public IWorkspace workspace;
	public EclipseWorkspaceFileSystemProvider provider;
	public IProgressMonitor progress;


	public EclipseWorkspaceFileSystem(EclipseWorkspaceFileSystemProvider provider, IWorkspace workspace, IProgressMonitor progress) {
		this.provider = provider;
		this.workspace = workspace;
		this.progress = progress;
	}

	@Override
	public FileSystemProvider provider() {
		return provider;
	}

	@Override
	public void close() throws IOException {
	}

	@Override
	public boolean isOpen() {
		return true;
	}

	@Override
	public boolean isReadOnly() {
		return true;
	}

	@Override
	public String getSeparator() {
		return "/";
	}

	@Override
	public Iterable<Path> getRootDirectories() {
		return rootDirectories;
	}

	@Override
	public Iterable<FileStore> getFileStores() {
		return FileSystems.getDefault().getFileStores();
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		return FileSystems.getDefault().supportedFileAttributeViews();
	}

	@Override
	public Path getPath(String first, String... more) {
		Path result = root.resolve(first);
		for(String part : more) {
			result = result.resolve(part);
		}
		return result;
	}

	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		return FileSystems.getDefault().getPathMatcher(syntaxAndPattern);
	}

	@Override
	public UserPrincipalLookupService getUserPrincipalLookupService() {
		return FileSystems.getDefault().getUserPrincipalLookupService();
	}

	@Override
	public WatchService newWatchService() throws IOException {
		return FileSystems.getDefault().newWatchService();
	}

	public EclipseWorkspacePath getPath(IPath fullPath) {
		return root.of(fullPath);
	}

	public EclipseWorkspacePath getPath(IResource file) {
		return getPath(file.getFullPath());
	}
}