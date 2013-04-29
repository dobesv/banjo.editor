package banjo.ui.text;

import java.io.IOException;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultTextDoubleClickStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;

import banjo.dom.Comment;
import banjo.dom.Ellipsis;
import banjo.dom.HasFileRange;
import banjo.dom.Identifier;
import banjo.dom.NumberLiteral;
import banjo.dom.OperatorRef;
import banjo.dom.StringLiteral;
import banjo.dom.TokenVisitor;
import banjo.dom.UnitRef;
import banjo.dom.Whitespace;
import banjo.parser.BanjoScanner;
import banjo.parser.util.FileRange;
import banjo.parser.util.ParserReader;

public class BanjoDoubleClickStrategy extends DefaultTextDoubleClickStrategy {
	
	private final class WordFinder implements TokenVisitor<IRegion> {
		final int offset;
		final IDocument document;
		
		IRegion result = null;
		boolean found = false;

		
		
		public WordFinder(IDocument document, int offset) {
			super();
			this.offset = offset;
			this.document = document;
		}

		@Override
		public IRegion visitComment(Comment c) {
			return null;
		}

		@Override
		public IRegion visitEllipsis(Ellipsis ellipsis) {
			return tryToken(ellipsis);
		}

		@Override
		public IRegion visitEof(FileRange entireFileRange) {
			return result;
		}

		@Override
		public IRegion visitIdentifier(Identifier identifier) {
			return tryToken(identifier);
		}

		@Override
		public IRegion visitNumberLiteral(NumberLiteral numberLiteral) {
			return tryToken(numberLiteral);
		}

		@Override
		public IRegion visitOperator(OperatorRef operatorRef) {
			return tryToken(operatorRef);
		}

		@Override
		public IRegion visitStringLiteral(StringLiteral stringLiteral) {
			return null;
		}

		@Override
		public IRegion visitUnit(UnitRef unit) {
			return null;
		}

		@Override
		public IRegion visitWhitespace(Whitespace ws) {
			return null;
		}

		private IRegion tryToken(HasFileRange token) {
			final FileRange fileRange = token.getFileRange();
			if(!found && fileRange.containsOffset(offset)){
				found = true;
				result = new Region(fileRange.getStartOffset(), fileRange.length());
			}
			return null;
		}
	}
	protected final BanjoCharacterPairMatcher pairMatcher= new BanjoCharacterPairMatcher();
	protected final BanjoScanner scanner= new BanjoScanner();

	
	protected IRegion findAtom(IDocument document, int offset) {
		IRegion line;
		try {
			line= document.getLineInformationOfOffset(offset);
		} catch (BadLocationException e) {
			return null;
		}

		if (offset == line.getOffset() + line.getLength())
			return null;

		ParserReader in = ParserReader.fromSubstring("", document.get(), line.getOffset(), line.getOffset()+line.getLength());
		try {
			return scanner.<IRegion>scan(in, new WordFinder(document, offset));
		} catch (IOException e) {
			throw new Error(e); // Shouldn't happen
		}
		
	}
	
	@Override
	protected IRegion findExtendedDoubleClickSelection(IDocument document, int offset) {
		IRegion match= pairMatcher.match(document, offset);
		if (match != null && match.getLength() >= 2)
			return new Region(match.getOffset() + 1, match.getLength() - 2);
		return findAtom(document, offset);
	}

}