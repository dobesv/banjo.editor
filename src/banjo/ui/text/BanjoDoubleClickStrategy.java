package banjo.ui.text;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultTextDoubleClickStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;

import banjo.dom.token.TokenVisitor;
import banjo.parser.SourceCodeScanner;
import banjo.parser.util.FileRange;
import banjo.parser.util.ParserReader;

public class BanjoDoubleClickStrategy extends DefaultTextDoubleClickStrategy {

	private final class WordFinder implements TokenVisitor<WordFinder> {
		final int offset;
		final IRegion result;
		final boolean found;



		public WordFinder(int offset) {
			super();
			this.offset = offset;
			this.result = null;
			this.found = false;
		}

		public WordFinder(int offset, IRegion result) {
			super();
			this.offset = offset;
			this.result = result;
			this.found = true;
		}

		@Override
		public WordFinder comment(FileRange range, String text) {
			return this;
		}

		@Override
		public WordFinder eof(FileRange entireFileRange) {
			return this;
		}

		@Override
		public WordFinder identifier(FileRange range, String id) {
			return tryToken(range);
		}

		@Override
		public WordFinder numberLiteral(FileRange range, Number number, String text) {
			return tryToken(range);
		}

		@Override
		public WordFinder operator(FileRange range, String op) {
			return tryToken(range);
		}

		@Override
		public WordFinder stringLiteral(FileRange range, String text) {
			return this;
		}

		@Override
		public WordFinder whitespace(FileRange range, String text) {
			return this;
		}

		private WordFinder tryToken(FileRange fileRange) {
			if(!this.found && fileRange.containsOffset(this.offset)){
				return new WordFinder(offset, new Region(fileRange.getStartOffset(), fileRange.length()));
			}
			return this;
		}

		@Override
		public WordFinder badToken(FileRange fileRange, String text, String message) {
			return tryToken(fileRange);
		}
	}
	protected final BanjoCharacterPairMatcher pairMatcher= new BanjoCharacterPairMatcher();
	protected final SourceCodeScanner scanner= new SourceCodeScanner();


	protected IRegion findAtom(IDocument document, int offset) {
		IRegion line;
		try {
			line= document.getLineInformationOfOffset(offset);
		} catch (final BadLocationException e) {
			return null;
		}

		if (offset == line.getOffset() + line.getLength())
			return null;

		final ParserReader in = ParserReader.fromSubstring("", document.get(), line.getOffset(), line.getOffset()+line.getLength());
		try {
			return this.scanner.<WordFinder>scan(in, new WordFinder(offset)).result;
		} catch (final IOException e) {
			throw new UncheckedIOException(e); // Shouldn't happen
		}

	}

	@Override
	protected IRegion findExtendedDoubleClickSelection(IDocument document, int offset) {
		final IRegion match= this.pairMatcher.match(document, offset);
		if (match != null && match.getLength() >= 2)
			return new Region(match.getOffset() + 1, match.getLength() - 2);
		return findAtom(document, offset);
	}

}