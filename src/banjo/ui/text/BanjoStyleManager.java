package banjo.ui.text;

import java.util.HashMap;

import org.eclipse.jface.text.TextAttribute;

public class BanjoStyleManager {
	final HashMap<String,TextAttribute> styles = new HashMap<>();
	final BanjoColorManager colorManager;
	public BanjoStyleManager(BanjoColorManager colorManager) {
		new BanjoDefaultStyles().setStyles(colorManager, styles);
		this.colorManager = colorManager;
	}
	
	public TextAttribute getStyle(String name) {
		TextAttribute textAttribute = styles.get(name);
		if(textAttribute == null) {
			if(name.equals(BanjoStyleConstants.DEFAULT)) throw new Error();
			return getStyle(BanjoStyleConstants.DEFAULT);
		}
		return textAttribute;
	}

}
