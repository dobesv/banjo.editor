package banjo.ui.text;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.editors.text.FileDocumentProvider;

public class DocumentProvider extends FileDocumentProvider {

	protected IDocument createDocument(Object element) throws CoreException {
		IDocument document = super.createDocument(element);
//		if (document != null) {
//			IDocumentPartitioner partitioner =
//				new FastPartitioner(
//					new PartitionScanner(),
//					new String[] {
//						PartitionScanner.XML_TAG,
//						PartitionScanner.XML_COMMENT });
//			partitioner.connect(document);
//			document.setDocumentPartitioner(partitioner);
//		}
		return document;
	}
}