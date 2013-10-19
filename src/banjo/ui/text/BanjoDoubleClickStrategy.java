package banjo.ui.text;

import java.io.IOException;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultTextDoubleClickStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;

import banjo.dom.token.BadToken;
import banjo.dom.token.Comment;
import banjo.dom.token.Ellipsis;
import banjo.dom.token.Identifier;
import banjo.dom.token.NumberLiteral;
import banjo.dom.token.OperatorRef;
import banjo.dom.token.StringLiteral;
import banjo.dom.token.Token;
import banjo.dom.token.TokenVisitor;
import banjo.dom.token.Whitespace;
import banjo.parser.BanjoScanner;
import banjo.parser.util.FileRange;
import banjo.parser.util.ParserReader;

public class BanjoDoubleClickStrategy extends DefaultTextDoubleClickStrategy {

	private final class WordFinder implements TokenVisitor<IRegion> {
		final int offset;
		IRegion result = null;
		boolean found = false;



		public WordFinder(int offset) {
			super();
			this.offset = offset;
		}

		@Override
		public IRegion comment(FileRange range, Comment c) {
			return null;
		}

		@Override
		public IRegion ellipsis(FileRange range, Ellipsis ellipsis) {
			return tryToken(range, ellipsis);
		}

		@Override
		public IRegion eof(FileRange entireFileRange) {
			return this.result;
		}

		@Override
		public IRegion identifier(FileRange range, Identifier identifier) {
			return tryToken(range, identifier);
		}

		@Override
		public IRegion numberLiteral(FileRange range, NumberLiteral numberLiteral) {
			return tryToken(range, numberLiteral);
		}

		@Override
		public IRegion operator(FileRange range, OperatorRef operatorRef) {
			return tryToken(range, operatorRef);
		}

		@Override
		public IRegion stringLiteral(FileRange range, StringLiteral stringLiteral) {
			return null;
		}

		@Override
		public IRegion whitespace(FileRange range, Whitespace ws) {
			return null;
		}

		private IRegion tryToken(FileRange fileRange, Token token) {
			if(!this.found && fileRange.containsOffset(this.offset)){
				this.found = true;
				this.result = new Region(fileRange.getStartOffset(), fileRange.length());
			}
			return null;
		}

		@Override
		public IRegion badToken(FileRange fileRange, BadToken badToken) {
			return tryToken(fileRange, badToken);
		}
	}
	protected final BanjoCharacterPairMatcher pairMatcher= new BanjoCharacterPairMatcher();
	protected final BanjoScanner scanner= new BanjoScanner();


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
			return this.scanner.<IRegion>scan(in, new WordFinder(offset));
		} catch (final IOException e) {
			throw new Error(e); // Shouldn't happen
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