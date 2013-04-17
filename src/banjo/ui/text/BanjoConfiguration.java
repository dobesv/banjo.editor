package banjo.ui.text;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextDoubleClickStrategy;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;

public class BanjoConfiguration extends SourceViewerConfiguration {
	private BanjoDoubleClickStrategy doubleClickStrategy;
	private SourceScanner scanner;
	private BanjoColorManager colorManager;

	public BanjoConfiguration(BanjoColorManager colorManager) {
		this.colorManager = colorManager;
	}
	public ITextDoubleClickStrategy getDoubleClickStrategy(
		ISourceViewer sourceViewer,
		String contentType) {
		if (doubleClickStrategy == null)
			doubleClickStrategy = new BanjoDoubleClickStrategy();
		return doubleClickStrategy;
	}

	protected SourceScanner getScanner() {
		if (scanner == null) {
			scanner = new SourceScanner(colorManager);
		}
		return scanner;
	}

	public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
		PresentationReconciler reconciler = new PresentationReconciler();

		DefaultDamagerRepairer dr = new DefaultDamagerRepairer(getScanner());
		reconciler.setDamager(dr, IDocument.DEFAULT_CONTENT_TYPE);
		reconciler.setRepairer(dr, IDocument.DEFAULT_CONTENT_TYPE);

		return reconciler;
	}
	
	@Override
	public String[] getIndentPrefixes(ISourceViewer sourceViewer,
			String contentType) {
		// Hard-coded for 2 spaces for now
		return new String[] {"  ", " ", ""};
	}

}