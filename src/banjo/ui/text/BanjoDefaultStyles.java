package banjo.ui.text;

import java.util.Map;

import org.eclipse.jface.text.TextAttribute;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

public class BanjoDefaultStyles {

	//	Eclipse Default Colors:
	//
	//		fields = 0,0,192
	//		annotation = 100,100,100
	//		keywords = 127,0,85
	//		strings = 42,0,255
	//		comments = 63,127,95

	static final class DefaultColors {
		static final RGB COMMENT_COLOR = new RGB(127,127,127);
		static final RGB STRING_LITERAL = new RGB(42,0,255);
		static final RGB NUMBER_LITERAL = new RGB(42,0,255);

		static final RGB IDENTIFIER = new RGB(0, 0, 0);
		static final RGB UNRESOLVED_IDENTIFIER = new RGB(255, 0, 0);

		static final RGB DEFAULT = new RGB(0, 0, 0);
		static final RGB OPERATOR = new RGB(0, 0, 0);

		static final RGB LOCAL_FUNCTION = new RGB(0,0,0);
		static final RGB LOCAL_CONST = new RGB(0,0,0);
		static final RGB LOCAL_VALUE = new RGB(0,0,0);

		static final RGB PARAMETER = new RGB(0, 0, 0);

		static final RGB FIELD = new RGB(0,0,192);
		static final RGB SELF = new RGB(127,0,85);
		static final RGB SELF_FIELD = new RGB(0,0,192);
		static final RGB SELF_CONST = new RGB(0,0,192);
		static final RGB SELF_METHOD = new RGB(64,127,127);

		static final RGB LOCAL_1 = new RGB(82,150,150);
		static final RGB LOCAL_2 = new RGB(185,189,227);
		static final RGB LOCAL_3 = new RGB(132,91,75);
		static final RGB LOCAL_4 = new RGB(139,176,119);
		static final RGB LOCAL_5 = new RGB(127,150,115);

		static final RGB FUNCTION_1 = LOCAL_1;
		static final RGB FUNCTION_2 = LOCAL_2;
		static final RGB FUNCTION_3 = LOCAL_3;
		static final RGB FUNCTION_4 = LOCAL_4;
		static final RGB FUNCTION_5 = LOCAL_5;

		static final RGB PARAMETER_1 = new RGB(150,56,106);
		static final RGB PARAMETER_2 = new RGB(74,29,53);
		static final RGB PARAMETER_3 = new RGB(163,144,8);
		static final RGB PARAMETER_4 = new RGB(23,91,99);
		static final RGB PARAMETER_5 = new RGB(62,123,130);

		static final RGB FIELD_1 = new RGB(42,150,150);
		static final RGB FIELD_2 = new RGB(85,112,127);
		static final RGB FIELD_3 = new RGB(132,39,45);
		static final RGB FIELD_4 = new RGB(176,173,75);
		static final RGB FIELD_5 = new RGB(150,150,115);

		static final RGB SELF_1 = PARAMETER_1;
		static final RGB SELF_2 = PARAMETER_2;
		static final RGB SELF_3 = PARAMETER_3;
		static final RGB SELF_4 = PARAMETER_4;
		static final RGB SELF_5 = PARAMETER_5;
	}

	static final class DefaultFonts {
		static final String DEFAULT = "DejaVu Sans Mono";
		static final String UNICODE_OPERATOR = "DejaVu Sans Mono";
	}

	public void setStyles(BanjoColorManager cm, Map<String,TextAttribute> styleMap) {
		Font unicodeOperatorFont = null;
		try {
			unicodeOperatorFont = new Font(Display.getCurrent(), DefaultFonts.UNICODE_OPERATOR, 12, SWT.NORMAL);
		} catch(final SWTError e) {
			unicodeOperatorFont = null;
		}
		styleMap.put(BanjoStyleConstants.COMMENT, new TextAttribute(cm.getColor(DefaultColors.COMMENT_COLOR)));
		styleMap.put(BanjoStyleConstants.STRING_LITERAL, new TextAttribute(cm.getColor(DefaultColors.STRING_LITERAL)));
		styleMap.put(BanjoStyleConstants.NUMBER_LITERAL, new TextAttribute(cm.getColor(DefaultColors.NUMBER_LITERAL)));
		styleMap.put(BanjoStyleConstants.IDENTIFIER, new TextAttribute(cm.getColor(DefaultColors.IDENTIFIER)));
		styleMap.put(BanjoStyleConstants.UNRESOLVED_IDENTIFIER, new TextAttribute(cm.getColor(DefaultColors.UNRESOLVED_IDENTIFIER), null, SWT.BOLD|SWT.ITALIC|SWT.UNDERLINE_ERROR));
		styleMap.put(BanjoStyleConstants.DEFAULT, new TextAttribute(cm.getColor(DefaultColors.DEFAULT)));
		styleMap.put(BanjoStyleConstants.FIELD, new TextAttribute(cm.getColor(DefaultColors.FIELD)));
		styleMap.put(BanjoStyleConstants.UNICODE_OPERATOR, new TextAttribute(cm.getColor(DefaultColors.OPERATOR), null, SWT.NORMAL, unicodeOperatorFont));
		styleMap.put(BanjoStyleConstants.OPERATOR, new TextAttribute(cm.getColor(DefaultColors.OPERATOR)));
		styleMap.put(BanjoStyleConstants.LOCAL_FUNCTION, new TextAttribute(cm.getColor(DefaultColors.LOCAL_FUNCTION)));
		styleMap.put(BanjoStyleConstants.LOCAL_CONST, new TextAttribute(cm.getColor(DefaultColors.LOCAL_CONST)));
		styleMap.put(BanjoStyleConstants.LOCAL_VALUE, new TextAttribute(cm.getColor(DefaultColors.LOCAL_VALUE)));
		styleMap.put(BanjoStyleConstants.PARAMETER, new TextAttribute(cm.getColor(DefaultColors.PARAMETER), null, SWT.BOLD));
		styleMap.put(BanjoStyleConstants.SELF, new TextAttribute(cm.getColor(DefaultColors.SELF)));
		styleMap.put(BanjoStyleConstants.SELF_FIELD, new TextAttribute(cm.getColor(DefaultColors.SELF_FIELD)));
		styleMap.put(BanjoStyleConstants.SELF_CONST, new TextAttribute(cm.getColor(DefaultColors.SELF_CONST)));
		styleMap.put(BanjoStyleConstants.SELF_METHOD, new TextAttribute(cm.getColor(DefaultColors.SELF_METHOD)));

		styleMap.put(BanjoStyleConstants.LOCAL_1, new TextAttribute(cm.getColor(DefaultColors.LOCAL_1)));
		styleMap.put(BanjoStyleConstants.LOCAL_2, new TextAttribute(cm.getColor(DefaultColors.LOCAL_2)));
		styleMap.put(BanjoStyleConstants.LOCAL_3, new TextAttribute(cm.getColor(DefaultColors.LOCAL_3)));
		styleMap.put(BanjoStyleConstants.LOCAL_4, new TextAttribute(cm.getColor(DefaultColors.LOCAL_4)));
		styleMap.put(BanjoStyleConstants.LOCAL_5, new TextAttribute(cm.getColor(DefaultColors.LOCAL_5)));

		styleMap.put(BanjoStyleConstants.FUNCTION_1, new TextAttribute(cm.getColor(DefaultColors.FUNCTION_1), null, SWT.ITALIC));
		styleMap.put(BanjoStyleConstants.FUNCTION_2, new TextAttribute(cm.getColor(DefaultColors.FUNCTION_2), null, SWT.ITALIC));
		styleMap.put(BanjoStyleConstants.FUNCTION_3, new TextAttribute(cm.getColor(DefaultColors.FUNCTION_3), null, SWT.ITALIC));
		styleMap.put(BanjoStyleConstants.FUNCTION_4, new TextAttribute(cm.getColor(DefaultColors.FUNCTION_4), null, SWT.ITALIC));
		styleMap.put(BanjoStyleConstants.FUNCTION_5, new TextAttribute(cm.getColor(DefaultColors.FUNCTION_5), null, SWT.ITALIC));

		styleMap.put(BanjoStyleConstants.PARAMETER_1, new TextAttribute(cm.getColor(DefaultColors.PARAMETER_1), null, SWT.UNDERLINE_SINGLE));
		styleMap.put(BanjoStyleConstants.PARAMETER_2, new TextAttribute(cm.getColor(DefaultColors.PARAMETER_2), null, SWT.UNDERLINE_SINGLE));
		styleMap.put(BanjoStyleConstants.PARAMETER_3, new TextAttribute(cm.getColor(DefaultColors.PARAMETER_3), null, SWT.UNDERLINE_SINGLE));
		styleMap.put(BanjoStyleConstants.PARAMETER_4, new TextAttribute(cm.getColor(DefaultColors.PARAMETER_4), null, SWT.UNDERLINE_SINGLE));
		styleMap.put(BanjoStyleConstants.PARAMETER_5, new TextAttribute(cm.getColor(DefaultColors.PARAMETER_5), null, SWT.UNDERLINE_SINGLE));

		styleMap.put(BanjoStyleConstants.FIELD_1, new TextAttribute(cm.getColor(DefaultColors.FIELD_1)));
		styleMap.put(BanjoStyleConstants.FIELD_2, new TextAttribute(cm.getColor(DefaultColors.FIELD_2)));
		styleMap.put(BanjoStyleConstants.FIELD_3, new TextAttribute(cm.getColor(DefaultColors.FIELD_3)));
		styleMap.put(BanjoStyleConstants.FIELD_4, new TextAttribute(cm.getColor(DefaultColors.FIELD_4)));
		styleMap.put(BanjoStyleConstants.FIELD_5, new TextAttribute(cm.getColor(DefaultColors.FIELD_5)));

		styleMap.put(BanjoStyleConstants.SELF_1, new TextAttribute(cm.getColor(DefaultColors.SELF_1)));
		styleMap.put(BanjoStyleConstants.SELF_2, new TextAttribute(cm.getColor(DefaultColors.SELF_2)));
		styleMap.put(BanjoStyleConstants.SELF_3, new TextAttribute(cm.getColor(DefaultColors.SELF_3)));
		styleMap.put(BanjoStyleConstants.SELF_4, new TextAttribute(cm.getColor(DefaultColors.SELF_4)));
		styleMap.put(BanjoStyleConstants.SELF_5, new TextAttribute(cm.getColor(DefaultColors.SELF_5)));

	}

}
