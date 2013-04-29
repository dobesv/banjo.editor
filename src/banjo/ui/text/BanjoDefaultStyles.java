package banjo.ui.text;

import java.util.Map;

import org.eclipse.jface.text.TextAttribute;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGB;

public class BanjoDefaultStyles {
	
//	Eclipse Default Colors:
//
//		fields = 0,0,192
//		annotation = 100,100,100
//		keywords = 127,0,85
//		strings = 42,0,255
//		comments = 63,127,95
		
	RGB COMMENT = new RGB(127,127,127);
	RGB STRING_LITERAL = new RGB(42,0,255);
	RGB NUMBER_LITERAL = new RGB(42,0,255);
	
	RGB IDENTIFIER = new RGB(0, 0, 0);
	RGB UNRESOLVED_IDENTIFIER = new RGB(255, 0, 0);
	
	RGB DEFAULT = new RGB(0, 0, 0);
	RGB OPERATOR = new RGB(0, 0, 0);
	
	RGB LOCAL_FUNCTION = new RGB(0,0,0);
	RGB LOCAL_CONST = new RGB(0,0,0);
	RGB LOCAL_VALUE = new RGB(0,0,0);
	
	RGB PARAMETER = new RGB(0, 0, 0);
	
	RGB FIELD = new RGB(0,0,192);
	RGB SELF = new RGB(127,0,85);
	RGB SELF_FIELD = new RGB(0,0,192);
	RGB SELF_CONST = new RGB(0,0,192);
	RGB SELF_METHOD = new RGB(64,127,127);
	
	RGB LOCAL_1 = new RGB(82,150,150);
	RGB LOCAL_2 = new RGB(185,189,227);
	RGB LOCAL_3 = new RGB(132,91,75);
	RGB LOCAL_4 = new RGB(139,176,119);
	RGB LOCAL_5 = new RGB(127,150,115);

	RGB FUNCTION_1 = LOCAL_1;
	RGB FUNCTION_2 = LOCAL_2;
	RGB FUNCTION_3 = LOCAL_3;
	RGB FUNCTION_4 = LOCAL_4;
	RGB FUNCTION_5 = LOCAL_5;
	
	RGB PARAMETER_1 = new RGB(150,56,106);
	RGB PARAMETER_2 = new RGB(74,29,53);
	RGB PARAMETER_3 = new RGB(163,144,8);
	RGB PARAMETER_4 = new RGB(23,91,99);
	RGB PARAMETER_5 = new RGB(62,123,130);
	
	RGB FIELD_1 = new RGB(42,150,150);
	RGB FIELD_2 = new RGB(85,112,127);
	RGB FIELD_3 = new RGB(132,39,45);
	RGB FIELD_4 = new RGB(176,173,75);
	RGB FIELD_5 = new RGB(150,150,115);

	RGB SELF_1 = PARAMETER_1;
	RGB SELF_2 = PARAMETER_2;
	RGB SELF_3 = PARAMETER_3;
	RGB SELF_4 = PARAMETER_4;
	RGB SELF_5 = PARAMETER_5;
	
	public void setStyles(BanjoColorManager cm, Map<String,TextAttribute> styleMap) {
		styleMap.put(BanjoStyleConstants.COMMENT, new TextAttribute(cm.getColor(COMMENT)));
		styleMap.put(BanjoStyleConstants.STRING_LITERAL, new TextAttribute(cm.getColor(STRING_LITERAL)));
		styleMap.put(BanjoStyleConstants.NUMBER_LITERAL, new TextAttribute(cm.getColor(NUMBER_LITERAL)));
		styleMap.put(BanjoStyleConstants.IDENTIFIER, new TextAttribute(cm.getColor(IDENTIFIER)));
		styleMap.put(BanjoStyleConstants.UNRESOLVED_IDENTIFIER, new TextAttribute(cm.getColor(UNRESOLVED_IDENTIFIER), null, SWT.BOLD|SWT.ITALIC|SWT.UNDERLINE_ERROR));
		styleMap.put(BanjoStyleConstants.DEFAULT, new TextAttribute(cm.getColor(DEFAULT)));
		styleMap.put(BanjoStyleConstants.FIELD, new TextAttribute(cm.getColor(FIELD)));
		styleMap.put(BanjoStyleConstants.OPERATOR, new TextAttribute(cm.getColor(OPERATOR)));
		styleMap.put(BanjoStyleConstants.LOCAL_FUNCTION, new TextAttribute(cm.getColor(LOCAL_FUNCTION)));
		styleMap.put(BanjoStyleConstants.LOCAL_CONST, new TextAttribute(cm.getColor(LOCAL_CONST)));
		styleMap.put(BanjoStyleConstants.LOCAL_VALUE, new TextAttribute(cm.getColor(LOCAL_VALUE)));
		styleMap.put(BanjoStyleConstants.PARAMETER, new TextAttribute(cm.getColor(PARAMETER), null, SWT.BOLD));		
		styleMap.put(BanjoStyleConstants.SELF, new TextAttribute(cm.getColor(SELF)));
		styleMap.put(BanjoStyleConstants.SELF_FIELD, new TextAttribute(cm.getColor(SELF_FIELD)));
		styleMap.put(BanjoStyleConstants.SELF_CONST, new TextAttribute(cm.getColor(SELF_CONST)));
		styleMap.put(BanjoStyleConstants.SELF_METHOD, new TextAttribute(cm.getColor(SELF_METHOD)));
		
		styleMap.put(BanjoStyleConstants.LOCAL_1, new TextAttribute(cm.getColor(LOCAL_1)));
		styleMap.put(BanjoStyleConstants.LOCAL_2, new TextAttribute(cm.getColor(LOCAL_2)));
		styleMap.put(BanjoStyleConstants.LOCAL_3, new TextAttribute(cm.getColor(LOCAL_3)));
		styleMap.put(BanjoStyleConstants.LOCAL_4, new TextAttribute(cm.getColor(LOCAL_4)));
		styleMap.put(BanjoStyleConstants.LOCAL_5, new TextAttribute(cm.getColor(LOCAL_5)));

		styleMap.put(BanjoStyleConstants.FUNCTION_1, new TextAttribute(cm.getColor(FUNCTION_1), null, SWT.ITALIC));
		styleMap.put(BanjoStyleConstants.FUNCTION_2, new TextAttribute(cm.getColor(FUNCTION_2), null, SWT.ITALIC));
		styleMap.put(BanjoStyleConstants.FUNCTION_3, new TextAttribute(cm.getColor(FUNCTION_3), null, SWT.ITALIC));
		styleMap.put(BanjoStyleConstants.FUNCTION_4, new TextAttribute(cm.getColor(FUNCTION_4), null, SWT.ITALIC));
		styleMap.put(BanjoStyleConstants.FUNCTION_5, new TextAttribute(cm.getColor(FUNCTION_5), null, SWT.ITALIC));
		
		styleMap.put(BanjoStyleConstants.PARAMETER_1, new TextAttribute(cm.getColor(PARAMETER_1), null, SWT.UNDERLINE_SINGLE));
		styleMap.put(BanjoStyleConstants.PARAMETER_2, new TextAttribute(cm.getColor(PARAMETER_2), null, SWT.UNDERLINE_SINGLE));
		styleMap.put(BanjoStyleConstants.PARAMETER_3, new TextAttribute(cm.getColor(PARAMETER_3), null, SWT.UNDERLINE_SINGLE));
		styleMap.put(BanjoStyleConstants.PARAMETER_4, new TextAttribute(cm.getColor(PARAMETER_4), null, SWT.UNDERLINE_SINGLE));
		styleMap.put(BanjoStyleConstants.PARAMETER_5, new TextAttribute(cm.getColor(PARAMETER_5), null, SWT.UNDERLINE_SINGLE));

		styleMap.put(BanjoStyleConstants.FIELD_1, new TextAttribute(cm.getColor(FIELD_1)));
		styleMap.put(BanjoStyleConstants.FIELD_2, new TextAttribute(cm.getColor(FIELD_2)));
		styleMap.put(BanjoStyleConstants.FIELD_3, new TextAttribute(cm.getColor(FIELD_3)));
		styleMap.put(BanjoStyleConstants.FIELD_4, new TextAttribute(cm.getColor(FIELD_4)));
		styleMap.put(BanjoStyleConstants.FIELD_5, new TextAttribute(cm.getColor(FIELD_5)));

		styleMap.put(BanjoStyleConstants.SELF_1, new TextAttribute(cm.getColor(SELF_1)));
		styleMap.put(BanjoStyleConstants.SELF_2, new TextAttribute(cm.getColor(SELF_2)));
		styleMap.put(BanjoStyleConstants.SELF_3, new TextAttribute(cm.getColor(SELF_3)));
		styleMap.put(BanjoStyleConstants.SELF_4, new TextAttribute(cm.getColor(SELF_4)));
		styleMap.put(BanjoStyleConstants.SELF_5, new TextAttribute(cm.getColor(SELF_5)));
		
	}
	
}
