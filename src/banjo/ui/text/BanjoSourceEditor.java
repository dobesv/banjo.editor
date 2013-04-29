package banjo.ui.text;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.texteditor.SourceViewerDecorationSupport;

public class BanjoSourceEditor extends TextEditor {
	private static final String EDITOR_MATCHING_BRACKETS = "matchingBrackets";
	private static final String EDITOR_MATCHING_BRACKETS_COLOR = "matchingBracketsColor";
	
	private BanjoColorManager colorManager;
	private BanjoStyleManager styleManager;

	public BanjoSourceEditor() {
		super();
		colorManager = new BanjoColorManager();
		styleManager = new BanjoStyleManager(colorManager);
		setSourceViewerConfiguration(new BanjoConfiguration(colorManager, styleManager));
		setDocumentProvider(new DocumentProvider());
		installTabsToSpacesConverter();
	}
	
	public void dispose() {
		colorManager.dispose();
		super.dispose();
	}
	
	@Override
	protected void configureSourceViewerDecorationSupport(SourceViewerDecorationSupport support) {
		super.configureSourceViewerDecorationSupport(support);
		support.setCharacterPairMatcher(new BanjoCharacterPairMatcher());
		support.setMatchingCharacterPainterPreferenceKeys(EDITOR_MATCHING_BRACKETS, EDITOR_MATCHING_BRACKETS_COLOR);
		IPreferenceStore store = getPreferenceStore();
		store.setDefault(EDITOR_MATCHING_BRACKETS, true);
		store.setDefault(EDITOR_MATCHING_BRACKETS_COLOR, "128,128,128");
	}

	@Override
	protected boolean isTabsToSpacesConversionEnabled() {
		return true;
	}
}
