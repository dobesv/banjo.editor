package banjo.ui.text;

import org.eclipse.jface.text.source.DefaultCharacterPairMatcher;

public class BanjoCharacterPairMatcher extends DefaultCharacterPairMatcher {
	public static final char[] DEFAULT_PAIRS = {'(', ')', '{', '}', '[', ']'};
	public BanjoCharacterPairMatcher(char[] chars) {
		super(chars, BanjoPartitions.BANJO_PARTITIONING);
	}

	public BanjoCharacterPairMatcher() {
		this(DEFAULT_PAIRS);
	}
}
