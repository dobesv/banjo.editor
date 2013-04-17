package banjo.ui.text;

import org.eclipse.swt.graphics.RGB;

public interface BanjoColorConstants {
	
//	Eclipse Default Colors:
//
//		fields = 0,0,192
//		annotation = 100,100,100
//		keywords = 127,0,85
//		strings = 42,0,255
//		comments = 63,127,95
		
	RGB COMMENT = new RGB(63,127,95);
	RGB STRING_LITERAL = new RGB(42,0,255);
	RGB NUMBER_LITERAL = new RGB(42,0,255);
	RGB IDENTIFIER = new RGB(0, 0, 0);
	RGB DEFAULT = new RGB(0, 0, 0);
	RGB OPERATOR = new RGB(0, 0, 0);
}
