package banjo.ui.text;

import org.eclipse.ui.editors.text.TextEditor;

public class SourceEditor extends TextEditor {

	private BanjoColorManager colorManager;

	public SourceEditor() {
		super();
		colorManager = new BanjoColorManager();
		setSourceViewerConfiguration(new BanjoConfiguration(colorManager));
		setDocumentProvider(new DocumentProvider());
	}
	
	public void dispose() {
		colorManager.dispose();
		super.dispose();
	}

}
