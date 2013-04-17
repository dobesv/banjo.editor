package banjo.ui.text;

import java.io.IOException;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.ITokenScanner;
import org.eclipse.jface.text.rules.Token;

import banjo.dom.Comment;
import banjo.dom.Ellipsis;
import banjo.dom.HasFileRange;
import banjo.dom.Identifier;
import banjo.dom.Key;
import banjo.dom.NumberLiteral;
import banjo.dom.OperatorRef;
import banjo.dom.StringLiteral;
import banjo.dom.TokenVisitor;
import banjo.dom.UnitRef;
import banjo.dom.Whitespace;
import banjo.parser.BanjoScanner;
import banjo.parser.util.FilePos;
import banjo.parser.util.ParserReader;

public class SourceScanner implements ITokenScanner, TokenVisitor<IToken> {

	private IDocument document;
	
	private char[][] legalLineDelimiters;
	private int rangeEnd;
	
	final IToken commentToken;
	final IToken operatorToken;
	final IToken identifierToken;
	final IToken stringLiteralToken;
	final IToken numberLiteralToken;
	final IToken fieldToken;
	final IToken defaultToken;
	private final BanjoScanner scanner = new BanjoScanner();
	private ParserReader in = null;

	private int tokenOffset;

	private int tokenLength;

	private boolean inProjection;
	
	public SourceScanner(BanjoColorManager manager) {
		commentToken = new Token(new TextAttribute(manager.getColor(BanjoColorConstants.COMMENT)));
		operatorToken = new Token(new TextAttribute(manager.getColor(BanjoColorConstants.OPERATOR)));
		identifierToken = new Token(new TextAttribute(manager.getColor(BanjoColorConstants.IDENTIFIER)));
		stringLiteralToken = new Token(new TextAttribute(manager.getColor(BanjoColorConstants.STRING_LITERAL)));
		numberLiteralToken = new Token(new TextAttribute(manager.getColor(BanjoColorConstants.NUMBER_LITERAL)));
		fieldToken = new Token(new TextAttribute(manager.getColor(BanjoColorConstants.FIELD)));
		defaultToken = new Token(new TextAttribute(manager.getColor(BanjoColorConstants.DEFAULT)));
	}

	/*
	 * @see ITokenScanner#setRange(IDocument, int, int)
	 */
	public void setRange(final IDocument document, int offset, int length) {
		Assert.isLegal(document != null);
		final int documentLength= document.getLength();
		checkRange(offset, length, documentLength);

		this.document = document;
		this.in = ParserReader.fromString("<source>", document.get());
		try { in.skip(offset); } catch (IOException e) { throw new Error(e); }
		this.rangeEnd = offset + length;

		String[] delimiters = document.getLegalLineDelimiters();
		this.legalLineDelimiters = new char[delimiters.length][];
		for (int i= 0; i < delimiters.length; i++)
			this.legalLineDelimiters[i] = delimiters[i].toCharArray();
	}


	/**
	 * Checks that the given range is valid.
	 * See https://bugs.eclipse.org/bugs/show_bug.cgi?id=69292
	 *
	 * @param offset the offset of the document range to scan
	 * @param length the length of the document range to scan
	 * @param documentLength the document's length
	 * @since 3.3
	 */
	private void checkRange(int offset, int length, int documentLength) {
		Assert.isLegal(offset > -1);
		Assert.isLegal(length > -1);
		Assert.isLegal(offset + length <= documentLength);
	}

	@Override
	public IToken nextToken() {
		try {
			return scanner.next(this.in, this);
		} catch (IOException e) {
			throw new Error(e); // Shouldn't happen with a string as input
		}
	}

	@Override
	public int getTokenOffset() {
		return tokenOffset;
	}

	@Override
	public int getTokenLength() {
		return tokenLength;
	}

	IToken token(IToken token, int offset, int length) {
		this.tokenOffset = offset;
		this.tokenLength = length;
		if(token != Token.WHITESPACE && token != commentToken)
			inProjection = false;
		return token;
	}
	
	IToken token(IToken token, HasFileRange sourceToken) {
		return token(token, sourceToken.getStartOffset(), sourceToken.getFileRange().length());
	}
	
	IToken token(IToken normalToken, IToken fieldToken, Key key) {
		return token(inProjection?fieldToken:normalToken, key);
	}
	
	@Override
	public IToken visitWhitespace(Whitespace ws) {
		return token(Token.WHITESPACE, ws);
	}

	@Override
	public IToken visitComment(Comment com) {
		return token(commentToken, com);
	}

	@Override
	public IToken visitEof(FilePos endPos) {
		return token(Token.EOF, tokenOffset+tokenLength, 0);
	}

	@Override
	public IToken visitOperator(OperatorRef or) {
		IToken result = token(operatorToken, or);
		if(or.getOp().equals(".")) {
			inProjection = true;
		}
		return result;
	}

	@Override
	public IToken visitStringLiteral(StringLiteral lit) {
		return token(stringLiteralToken, fieldToken, lit);
	}

	@Override
	public IToken visitNumberLiteral(NumberLiteral lit) {
		return token(numberLiteralToken, lit);
	}

	@Override
	public IToken visitIdentifier(Identifier ident) {
		return token(identifierToken, fieldToken, ident);
	}

	@Override
	public IToken visitEllipsis(Ellipsis ellipsis) {
		return token(identifierToken, ellipsis);
	}

	@Override
	public IToken visitUnit(UnitRef unit) {
		return token(identifierToken, unit);
	}
}
