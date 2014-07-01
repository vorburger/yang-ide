/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package com.cisco.yangide.editor.editors;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultIndentLineAutoEditStrategy;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.DocumentRewriteSession;
import org.eclipse.jface.text.DocumentRewriteSessionType;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.rules.FastPartitioner;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.texteditor.ITextEditorExtension3;
import org.eclipse.jdt.internal.ui.text.JavaHeuristicScanner;
import org.eclipse.jdt.ui.text.IJavaPartitions;

import com.cisco.yangide.editor.YangEditorPlugin;
import com.cisco.yangide.editor.editors.text.YangHeuristicScanner;
import com.cisco.yangide.editor.editors.text.YangIndenter;
import com.cisco.yangide.editor.preferences.YangPreferenceConstants;

/**
 * @author Alexey Kholupko Auto indent strategy sensitive to brackets.
 */
public class YangAutoIndentStrategy extends DefaultIndentLineAutoEditStrategy {

    /** The line comment introducer. Value is "{@value} " */
    private static final String LINE_COMMENT = "//"; //$NON-NLS-1$
    //XXX  Useless yet
    private static final int DEFAULT_TAB_WIDTH = 2;
    private static final boolean DEFAULT_SPACES_FOR_TAB = true;


    private boolean fCloseBrace;
    private boolean fIsSmartMode;
    private boolean fIsSmartTab;
    private boolean fIsSmartIndentAfterNewline;

    private String fPartitioning;
    /**
     * The viewer.
     * 
     * @since 3.5
     */
    private final ISourceViewer fViewer;

    /**
     * Creates a new Java auto indent strategy for the given document partitioning.
     * 
     * @param partitioning the document partitioning
     * @param project the project to get formatting preferences from, or null to use default
     * preferences
     * @param viewer the source viewer that this strategy is attached to
     */
    public YangAutoIndentStrategy(String partitioning, ISourceViewer viewer) {
        fPartitioning = partitioning;
        fViewer = viewer;
    }

    private int getBracketCount(IDocument d, int startOffset, int endOffset, boolean ignoreCloseBrackets)
            throws BadLocationException {

        int bracketCount = 0;
        while (startOffset < endOffset) {
            char curr = d.getChar(startOffset);
            startOffset++;
            switch (curr) {
            case '/':
                if (startOffset < endOffset) {
                    char next = d.getChar(startOffset);
                    if (next == '*') {
                        // a comment starts, advance to the comment end
                        startOffset = getCommentEnd(d, startOffset + 1, endOffset);
                    } else if (next == '/') {
                        // '//'-comment: nothing to do anymore on this line
                        startOffset = endOffset;
                    }
                }
                break;
            case '*':
                if (startOffset < endOffset) {
                    char next = d.getChar(startOffset);
                    if (next == '/') {
                        // we have been in a comment: forget what we read before
                        bracketCount = 0;
                        startOffset++;
                    }
                }
                break;
            case '{':
                bracketCount++;
                ignoreCloseBrackets = false;
                break;
            case '}':
                if (!ignoreCloseBrackets) {
                    bracketCount--;
                }
                break;
            case '"':
            case '\'':
                startOffset = getStringEnd(d, startOffset, endOffset, curr);
                break;
            default:
            }
        }
        return bracketCount;
    }

    // ----------- bracket counting ------------------------------------------------------

    private int getCommentEnd(IDocument d, int offset, int endOffset) throws BadLocationException {
        while (offset < endOffset) {
            char curr = d.getChar(offset);
            offset++;
            if (curr == '*') {
                if (offset < endOffset && d.getChar(offset) == '/') {
                    return offset + 1;
                }
            }
        }
        return endOffset;
    }

    private String getIndentOfLine(IDocument d, int line) throws BadLocationException {
        if (line > -1) {
            int start = d.getLineOffset(line);
            int end = start + d.getLineLength(line) - 1;
            int whiteEnd = findEndOfWhiteSpace(d, start, end);
            return d.get(start, whiteEnd - start);
        } else {
            return ""; //$NON-NLS-1$
        }
    }

    private int getStringEnd(IDocument d, int offset, int endOffset, char ch) throws BadLocationException {
        while (offset < endOffset) {
            char curr = d.getChar(offset);
            offset++;
            if (curr == '\\') {
                // ignore escaped characters
                offset++;
            } else if (curr == ch) {
                return offset;
            }
        }
        return endOffset;
    }
/*
    private void smartIndentAfterClosingBracket(IDocument d, DocumentCommand c) {
        if (c.offset == -1 || d.getLength() == 0)
            return;

        try {
            
            int p = (c.offset == d.getLength() ? c.offset - 1 : c.offset);
            int line = d.getLineOfOffset(p);
            int start = d.getLineOffset(line);
            int whiteend = findEndOfWhiteSpace(d, start, c.offset);

            YangHeuristicScanner scanner = new YangHeuristicScanner(d);
            YangIndenter indenter = new YangIndenter(d, scanner);

            // shift only when line does not contain any text up to the closing bracket
            if (whiteend == c.offset) {
                // evaluate the line with the opening bracket that matches out closing bracket
                int reference = indenter.findReferencePosition(c.offset, false, true, false, false);
                int indLine = d.getLineOfOffset(reference);
                if (indLine != -1 && indLine != line) {
                    // take the indent of the found line
                    StringBuffer replaceText = new StringBuffer(getIndentOfLine(d, indLine));
                    // add the rest of the current line including the just added close bracket
                    replaceText.append(d.get(whiteend, c.offset - whiteend));
                    replaceText.append(c.text);
                    // modify document command
                    c.length += c.offset - start;
                    c.offset = start;
                    c.text = replaceText.toString();
                }
            }
        } catch (BadLocationException e) {
            YangEditorPlugin.log(e);
        }
    }

*/    private void smartIndentAfterOpeningBracket(IDocument d, DocumentCommand c) {
        if (c.offset < 1 || d.getLength() == 0)
            return;

        YangHeuristicScanner scanner = new YangHeuristicScanner(d);

        int p = (c.offset == d.getLength() ? c.offset - 1 : c.offset);

        try {
            // current line
            int line = d.getLineOfOffset(p);
            int lineOffset = d.getLineOffset(line);

            // make sure we don't have any leading comments etc.
            if (d.get(lineOffset, p - lineOffset).trim().length() != 0)
                return;

            // line of last Java code
            int pos = scanner.findNonWhitespaceBackward(p, YangHeuristicScanner.UNBOUND);
            if (pos == -1)
                return;
            int lastLine = d.getLineOfOffset(pos);

            // only shift if the last java line is further up and is a braceless block candidate
            if (lastLine < line) {

                YangIndenter indenter = new YangIndenter(d, scanner);
                StringBuffer indent = indenter.computeIndentation(p, true);
                String toDelete = d.get(lineOffset, c.offset - lineOffset);
                if (indent != null && !indent.toString().equals(toDelete)) {
                    c.text = indent.append(c.text).toString();
                    c.length += c.offset - lineOffset;
                    c.offset = lineOffset;
                }
            }

        } catch (BadLocationException e) {
            YangEditorPlugin.log(e);
        }

    }

    private void smartIndentAfterNewLine(IDocument d, DocumentCommand c) {
        YangHeuristicScanner scanner = new YangHeuristicScanner(d);
        YangIndenter indenter = new YangIndenter(d, scanner);
        StringBuffer indent = indenter.computeIndentation(c.offset);
        if (indent == null)
            indent = new StringBuffer();

        //indent.append("    ");
        
        int docLength = d.getLength();
        if (c.offset == -1 || docLength == 0)
            return;

        try {
            int p = (c.offset == docLength ? c.offset - 1 : c.offset);
            int line = d.getLineOfOffset(p);

            StringBuffer buf = new StringBuffer(c.text + indent);

            IRegion reg = d.getLineInformation(line);
            int lineEnd = reg.getOffset() + reg.getLength();

            int contentStart = findEndOfWhiteSpace(d, c.offset, lineEnd);
            c.length = Math.max(contentStart - c.offset, 0);

            int start = reg.getOffset();
            ITypedRegion region = TextUtilities.getPartition(d, fPartitioning, start, true);
//            if (IJavaPartitions.JAVA_DOC.equals(region.getType()))
//                start = d.getLineInformationOfOffset(region.getOffset()).getOffset();

            // insert closing brace on new line after an unclosed opening brace
            // TODO
            if (getBracketCount(d, start, c.offset, true) > 0 && closeBrace() && !isClosed(d, c.offset, c.length)) {
                c.caretOffset = c.offset + buf.length();
                c.shiftsCaret = false;

                // copy old content of line behind insertion point to new line
                // unless we think we are inserting an anonymous type definition

                if (c.offset == 0) {
                    if (lineEnd - contentStart > 0) {
                        c.length = lineEnd - c.offset;
                        buf.append(d.get(contentStart, lineEnd - contentStart).toCharArray());
                    }
                }

                buf.append(TextUtilities.getDefaultLineDelimiter(d));
                StringBuffer reference = null;
                int nonWS = findEndOfWhiteSpace(d, start, lineEnd);
                if (nonWS < c.offset && d.getChar(nonWS) == '{')
                    reference = new StringBuffer(d.get(start, nonWS - start));
                else
                    reference = indenter.getReferenceIndentation(c.offset);
                if (reference != null)
                    buf.append(reference);
                buf.append('}');
            }
            // insert extra line upon new line between two braces
            else if (c.offset > start && contentStart < lineEnd && d.getChar(contentStart) == '}') {
                int firstCharPos = scanner.findNonWhitespaceBackward(c.offset - 1, start);
                if (firstCharPos != YangHeuristicScanner.NOT_FOUND && d.getChar(firstCharPos) == '{') {
                    c.caretOffset = c.offset + buf.length();
                    c.shiftsCaret = false;

                    StringBuffer reference = null;
                    int nonWS = findEndOfWhiteSpace(d, start, lineEnd);
                    if (nonWS < c.offset && d.getChar(nonWS) == '{')
                        reference = new StringBuffer(d.get(start, nonWS - start));
                    else
                        reference = indenter.getReferenceIndentation(c.offset);

                    buf.append(TextUtilities.getDefaultLineDelimiter(d));

                    if (reference != null)
                        buf.append(reference);
                }
            }
            c.text = buf.toString();

        } catch (BadLocationException e) {
            YangEditorPlugin.log(e);
        }
    }

    /**
     * Computes an insert position for an opening brace if <code>offset</code> maps to a position in
     * <code>document</code> with a expression in parenthesis that will take a block after the
     * closing parenthesis.
     * 
     * @param document the document being modified
     * @param offset the offset of the caret position, relative to the line start.
     * @param partitioning the document partitioning
     * @param max the max position
     * @return an insert position relative to the line start if <code>line</code> contains a
     * parenthesized expression that can be followed by a block, -1 otherwise
     */
/*    private static int computeAnonymousPosition(IDocument document, int offset, String partitioning, int max) {
        // find the opening parenthesis for every closing parenthesis on the current line after
        // offset
        // return the position behind the closing parenthesis if it looks like a method declaration
        // or an expression for an if, while, for, catch statement

        YangHeuristicScanner scanner = new YangHeuristicScanner(document);
        int pos = offset;
        int length = max;
        int scanTo = scanner.scanForward(pos, length, '}');
        if (scanTo == -1)
            scanTo = length;

        int closingParen = findClosingParenToLeft(scanner, pos) - 1;
        boolean hasNewToken = looksLikeAnonymousClassDef(document, partitioning, scanner, pos);
        int openingParen = -1;
        while (true) {
            int startScan = closingParen + 1;
            closingParen = scanner.scanForward(startScan, scanTo, ')');
            if (closingParen == -1) {
                if (hasNewToken && openingParen != -1)
                    return openingParen + 1;
                break;
            }

            openingParen = scanner.findOpeningPeer(closingParen - 1, '(', ')');

            // no way an expression at the beginning of the document can mean anything
            if (openingParen < 1)
                break;

            // only select insert positions for parenthesis currently embracing the caret
            if (openingParen > pos)
                continue;

            if (looksLikeAnonymousClassDef(document, partitioning, scanner, openingParen - 1))
                return closingParen + 1;

        }

        return -1;
    }
*/
    /**
     * Finds a closing parenthesis to the left of <code>position</code> in document, where that
     * parenthesis is only separated by whitespace from <code>position</code>. If no such
     * parenthesis can be found, <code>position</code> is returned.
     * 
     * @param scanner the java heuristic scanner set up on the document
     * @param position the first character position in <code>document</code> to be considered
     * @return the position of a closing parenthesis left to <code>position</code> separated only by
     * whitespace, or <code>position</code> if no parenthesis can be found
     */
    /*
    private static int findClosingParenToLeft(YangHeuristicScanner scanner, int position) {
        if (position < 1)
            return position;

        if (scanner.previousToken(position - 1, YangHeuristicScanner.UNBOUND) == Symbols.TokenRPAREN)
            return scanner.getPosition() + 1;
        return position;
    }
    */

    /**
     * Checks whether the content of <code>document</code> in the range (<code>offset</code>,
     * <code>length</code>) contains the <code>new</code> keyword.
     * 
     * @param document the document being modified
     * @param offset the first character position in <code>document</code> to be considered
     * @param length the length of the character range to be considered
     * @param partitioning the document partitioning
     * @return <code>true</code> if the specified character range contains a <code>new</code>
     * keyword, <code>false</code> otherwise.
     */
    private static boolean isNewMatch(IDocument document, int offset, int length, String partitioning) {
        Assert.isTrue(length >= 0);
        Assert.isTrue(offset >= 0);
        Assert.isTrue(offset + length < document.getLength() + 1);

        try {
            String text = document.get(offset, length);
            int pos = text.indexOf("new"); //$NON-NLS-1$

            while (pos != -1 && !isDefaultPartition(document, pos + offset, partitioning))
                pos = text.indexOf("new", pos + 2); //$NON-NLS-1$

            if (pos < 0)
                return false;

            if (pos != 0 && Character.isJavaIdentifierPart(text.charAt(pos - 1)))
                return false;

            if (pos + 3 < length && Character.isJavaIdentifierPart(text.charAt(pos + 3)))
                return false;

            return true;

        } catch (BadLocationException e) {
        }
        return false;
    }


    /**
     * Checks whether <code>position</code> resides in a default (Java) partition of
     * <code>document</code>.
     * 
     * @param document the document being modified
     * @param position the position to be checked
     * @param partitioning the document partitioning
     * @return <code>true</code> if <code>position</code> is in the default partition of
     * <code>document</code>, <code>false</code> otherwise
     */
    private static boolean isDefaultPartition(IDocument document, int position, String partitioning) {
        Assert.isTrue(position >= 0);
        Assert.isTrue(position <= document.getLength());

        try {
            ITypedRegion region = TextUtilities.getPartition(document, partitioning, position, false);
            return region.getType().equals(IDocument.DEFAULT_CONTENT_TYPE);

        } catch (BadLocationException e) {
        }

        return false;
    }

    /* TODO
     * @see org.eclipse.jdt.internal.ui.text.java.JavaAutoIndentStrategy#isClosed
     */
    private boolean isClosed(IDocument document, int offset, int length) {

        return getBlockBalance(document, offset, fPartitioning) <= 0;

    }


    /**
     * Installs a java partitioner with <code>document</code>.
     * 
     * @param document the document
     */
    private static void removeJavaStuff(Document document) {
        document.setDocumentPartitioner(IJavaPartitions.JAVA_PARTITIONING, null);
    }

    /*
    private void smartPaste(IDocument document, DocumentCommand command) {
        int newOffset = command.offset;
        int newLength = command.length;
        String newText = command.text;

        try {
            YangHeuristicScanner scanner = new YangHeuristicScanner(document);
            JavaIndenter indenter = new JavaIndenter(document, scanner, null);
            int offset = newOffset;

            // reference position to get the indent from
            int refOffset = indenter.findReferencePosition(offset);
            if (refOffset == YangHeuristicScanner.NOT_FOUND)
                return;
            int peerOffset = getPeerPosition(document, command);
            peerOffset = indenter.findReferencePosition(peerOffset);
            if (peerOffset != YangHeuristicScanner.NOT_FOUND)
                refOffset = Math.min(refOffset, peerOffset);

            // eat any WS before the insertion to the beginning of the line
            int firstLine = 1; // don't format the first line per default, as it has other content
                               // before it
            IRegion line = document.getLineInformationOfOffset(offset);
            String notSelected = document.get(line.getOffset(), offset - line.getOffset());
            if (notSelected.trim().length() == 0) {
                newLength += notSelected.length();
                newOffset = line.getOffset();
                firstLine = 0;
            }

            // prefix: the part we need for formatting but won't paste
            IRegion refLine = document.getLineInformationOfOffset(refOffset);
            String prefix = document.get(refLine.getOffset(), newOffset - refLine.getOffset());

            // handle the indentation computation inside a temporary document
            Document temp = new Document(prefix + newText);
            DocumentRewriteSession session = temp.startRewriteSession(DocumentRewriteSessionType.STRICTLY_SEQUENTIAL);
            scanner = new YangHeuristicScanner(temp);
            indenter = new JavaIndenter(temp, scanner, null);
            installJavaStuff(temp);

            // indent the first and second line
            // compute the relative indentation difference from the second line
            // (as the first might be partially selected) and use the value to
            // indent all other lines.
            boolean isIndentDetected = false;
            StringBuffer addition = new StringBuffer();
            int insertLength = 0;
            int firstLineInsertLength = 0;
            int firstLineIndent = 0;
            int first = document.computeNumberOfLines(prefix) + firstLine; // don't format first
                                                                           // line
            int lines = temp.getNumberOfLines();
            int tabLength = getVisualTabLengthPreference();
            boolean changed = false;
            for (int l = first; l < lines; l++) { // we don't change the number of lines while
                                                  // adding indents

                IRegion r = temp.getLineInformation(l);
                int lineOffset = r.getOffset();
                int lineLength = r.getLength();

                if (lineLength == 0) // don't modify empty lines
                    continue;

                if (!isIndentDetected) {

                    // indent the first pasted line
                    String current = getCurrentIndent(temp, l);
                    StringBuffer correct = indenter.computeIndentation(lineOffset);
                    if (correct == null)
                        return; // bail out

                    insertLength = subtractIndent(correct, current, addition, tabLength);
                    if (l == first) {
                        firstLineInsertLength = insertLength;
                        firstLineIndent = current.length();
                    }
                    if (l != first && temp.get(lineOffset, lineLength).trim().length() != 0) {
                        isIndentDetected = true;
                        if (firstLineIndent >= current.length())
                            insertLength = firstLineInsertLength;
                        if (insertLength == 0) {
                            // no adjustment needed, bail out
                            if (firstLine == 0) {
                                // but we still need to adjust the first line
                                command.offset = newOffset;
                                command.length = newLength;
                                if (changed)
                                    break; // still need to get the leading indent of the first line
                            }
                            return;
                        }
                    } else {
                        changed = insertLength != 0;
                    }
                }

                // relatively indent all pasted lines
                if (insertLength > 0)
                    addIndent(temp, l, addition, tabLength);
                else if (insertLength < 0)
                    cutIndent(temp, l, -insertLength, tabLength);

            }

            removeJavaStuff(temp);
            temp.stopRewriteSession(session);
            newText = temp.get(prefix.length(), temp.getLength() - prefix.length());

            command.offset = newOffset;
            command.length = newLength;
            command.text = newText;

        } catch (BadLocationException e) {
            YangEditorPlugin.log(e);
        }

    }
    */

    /**
     * Returns the indentation of the line <code>line</code> in <code>document</code>. The returned
     * string may contain pairs of leading slashes that are considered part of the indentation. The
     * space before the asterisk in a javadoc-like comment is not considered part of the
     * indentation.
     * 
     * @param document the document
     * @param line the line
     * @return the indentation of <code>line</code> in <code>document</code>
     * @throws BadLocationException if the document is changed concurrently
     */
    private static String getCurrentIndent(Document document, int line) throws BadLocationException {
        IRegion region = document.getLineInformation(line);
        int from = region.getOffset();
        int endOffset = region.getOffset() + region.getLength();

        // go behind line comments
        int to = from;
        while (to < endOffset - 2 && document.get(to, 2).equals(LINE_COMMENT))
            to += 2;

        while (to < endOffset) {
            char ch = document.getChar(to);
            if (!Character.isWhitespace(ch))
                break;
            to++;
        }

        // don't count the space before javadoc like, asterisk-style comment lines
        if (to > from && to < endOffset - 1 && document.get(to - 1, 2).equals(" *")) { //$NON-NLS-1$
            String type = TextUtilities.getContentType(document, IJavaPartitions.JAVA_PARTITIONING, to, true);
            if (type.equals(IJavaPartitions.JAVA_DOC) || type.equals(IJavaPartitions.JAVA_MULTI_LINE_COMMENT))
                to--;
        }

        return document.get(from, to - from);
    }

    /**
     * Computes the difference of two indentations and returns the difference in length of current
     * and correct. If the return value is positive, <code>addition</code> is initialized with a
     * substring of that length of <code>correct</code>.
     * 
     * @param correct the correct indentation
     * @param current the current indentation (might contain non-whitespace)
     * @param difference a string buffer - if the return value is positive, it will be cleared and
     * set to the substring of <code>current</code> of that length
     * @param tabLength the length of a tab
     * @return the difference in length of <code>correct</code> and <code>current</code>
     */
    private int subtractIndent(CharSequence correct, CharSequence current, StringBuffer difference, int tabLength) {
        int c1 = computeVisualLength(correct, tabLength);
        int c2 = computeVisualLength(current, tabLength);
        int diff = c1 - c2;
        if (diff <= 0)
            return diff;

        difference.setLength(0);
        int len = 0, i = 0;
        while (len < diff) {
            char c = correct.charAt(i++);
            difference.append(c);
            len += computeVisualLength(c, tabLength);
        }

        return diff;
    }

    /**
     * Indents line <code>line</code> in <code>document</code> with <code>indent</code>. Leaves
     * leading comment signs alone.
     * 
     * @param document the document
     * @param line the line
     * @param indent the indentation to insert
     * @param tabLength the length of a tab
     * @throws BadLocationException on concurrent document modification
     */
    private void addIndent(Document document, int line, CharSequence indent, int tabLength) throws BadLocationException {
        IRegion region = document.getLineInformation(line);
        int insert = region.getOffset();
        int endOffset = region.getOffset() + region.getLength();

        // Compute insert after all leading line comment markers
        int newInsert = insert;
        while (newInsert < endOffset - 2 && document.get(newInsert, 2).equals(LINE_COMMENT))
            newInsert += 2;

        // Heuristic to check whether it is commented code or just a comment
        if (newInsert > insert) {
            int whitespaceCount = 0;
            int i = newInsert;
            while (i < endOffset - 1) {
                char ch = document.get(i, 1).charAt(0);
                if (!Character.isWhitespace(ch))
                    break;
                whitespaceCount = whitespaceCount + computeVisualLength(ch, tabLength);
                i++;
            }

            // TODO
            if (whitespaceCount != 0)// && whitespaceCount >=
                                     // CodeFormatterUtil.getIndentWidth(fProject))
                insert = newInsert;
        }

        // Insert indent
        document.replace(insert, 0, indent.toString());
    }

    /**
     * Cuts the visual equivalent of <code>toDelete</code> characters out of the indentation of line
     * <code>line</code> in <code>document</code>. Leaves leading comment signs alone.
     * 
     * @param document the document
     * @param line the line
     * @param toDelete the number of space equivalents to delete
     * @param tabLength the length of a tab
     * @throws BadLocationException on concurrent document modification
     */
    private void cutIndent(Document document, int line, int toDelete, int tabLength) throws BadLocationException {
        IRegion region = document.getLineInformation(line);
        int from = region.getOffset();
        int endOffset = region.getOffset() + region.getLength();

        // go behind line comments
        while (from < endOffset - 2 && document.get(from, 2).equals(LINE_COMMENT))
            from += 2;

        int to = from;
        while (toDelete > 0 && to < endOffset) {
            char ch = document.getChar(to);
            if (!Character.isWhitespace(ch))
                break;
            toDelete -= computeVisualLength(ch, tabLength);
            if (toDelete >= 0)
                to++;
            else
                break;
        }

        document.replace(from, to - from, ""); //$NON-NLS-1$
    }

    /**
     * Returns the visual length of a given <code>CharSequence</code> taking into account the visual
     * tabulator length.
     * 
     * @param seq the string to measure
     * @param tabLength the length of a tab
     * @return the visual length of <code>seq</code>
     */
    private int computeVisualLength(CharSequence seq, int tabLength) {
        int size = 0;

        for (int i = 0; i < seq.length(); i++) {
            char ch = seq.charAt(i);
            if (ch == '\t') {
                if (tabLength != 0)
                    size += tabLength - size % tabLength;
                // else: size stays the same
            } else {
                size++;
            }
        }
        return size;
    }

    /**
     * Returns the visual length of a given character taking into account the visual tabulator
     * length.
     * 
     * @param ch the character to measure
     * @param tabLength the length of a tab
     * @return the visual length of <code>ch</code>
     */
    private int computeVisualLength(char ch, int tabLength) {
        if (ch == '\t')
            return tabLength;
        else
            return 1;
    }

    /**
     * The preference setting for the visual tabulator display.
     * 
     * @return the number of spaces displayed for a tabulator in the editor
     */
    private int getVisualTabLengthPreference() {
        // return CodeFormatterUtil.getTabWidth(fProject);
        return DEFAULT_TAB_WIDTH;
    }

    /**
     * The preference setting that tells whether to insert spaces when pressing the Tab key.
     * 
     * @return <code>true</code> if spaces are inserted when pressing the Tab key
     * @since 3.5
     */
    private boolean isInsertingSpacesForTab() {
        // return JavaCore.SPACE.equals(getCoreOption(fProject,
        // DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR));
        return DEFAULT_SPACES_FOR_TAB;
    }

  
    private boolean isLineDelimiter(IDocument document, String text) {
        String[] delimiters = document.getLegalLineDelimiters();
        if (delimiters != null)
            return TextUtilities.equals(delimiters, text) > -1;
        return false;
    }

    private void smartIndentOnKeypress(IDocument document, DocumentCommand command) {
        switch (command.text.charAt(0)) {
//        case '}':
//            smartIndentAfterClosingBracket(document, command);
//            break;
//        case '{':
//            smartIndentAfterOpeningBracket(document, command);
//            break;
        }
    }
    /*
     * @see
     * org.eclipse.jface.text.IAutoIndentStrategy#customizeDocumentCommand(org.eclipse.jface.text
     * .IDocument, org.eclipse.jface.text.DocumentCommand)
     */
    @Override
    public void customizeDocumentCommand(IDocument d, DocumentCommand c) {
        if (c.doit == false)
            return;

        clearCachedValues();

//        if (!fIsSmartMode) {
//            super.customizeDocumentCommand(d, c);
//            return;
//        }
//
//        if (!fIsSmartTab && isRepresentingTab(c.text))
//            return;

        if (c.length == 0 && c.text != null && isLineDelimiter(d, c.text)) {
            if (fIsSmartIndentAfterNewline)
                smartIndentAfterNewLine(d, c);
//            else
//                super.customizeDocumentCommand(d, c);
        } else if (c.text.length() == 1)
            smartIndentOnKeypress(d, c);

    }

    /**
     * Tells whether the given inserted string represents hitting the Tab key.
     * 
     * @param text the text to check
     * @return <code>true</code> if the text represents hitting the Tab key
     * @since 3.5
     */
    private boolean isRepresentingTab(String text) {
        if (text == null)
            return false;

        if (isInsertingSpacesForTab()) {
            if (text.length() == 0 || text.length() > getVisualTabLengthPreference())
                return false;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) != ' ')
                    return false;
            }
            return true;
        } else
            return text.length() == 1 && text.charAt(0) == '\t';
    }

    private static IPreferenceStore getPreferenceStore() {
        return YangEditorPlugin.getDefault().getPreferenceStore();
    }

    private boolean closeBrace() {
        return fCloseBrace;
    }

    private void clearCachedValues() {
        IPreferenceStore preferenceStore = getPreferenceStore();
        fCloseBrace = preferenceStore.getBoolean(YangPreferenceConstants.EDITOR_CLOSE_BRACES);
        fIsSmartTab = preferenceStore.getBoolean(YangPreferenceConstants.EDITOR_SMART_TAB);
        fIsSmartIndentAfterNewline = preferenceStore.getBoolean(YangPreferenceConstants.EDITOR_SMART_INDENT_AFTER_NEWLINE);
        fIsSmartMode = computeSmartMode();
    }

    private boolean computeSmartMode() {
        IWorkbenchPage page = YangEditorPlugin.getActivePage();
        if (page != null) {
            IEditorPart part = page.getActiveEditor();
            if (part instanceof ITextEditorExtension3) {
                ITextEditorExtension3 extension = (ITextEditorExtension3) part;
                return extension.getInsertMode() == ITextEditorExtension3.SMART_INSERT;
            }
        }
        return false;
    }
    
    /**
     * Returns the block balance, i.e. zero if the blocks are balanced at <code>offset</code>, a
     * negative number if there are more closing than opening braces, and a positive number if there
     * are more opening than closing braces.
     * 
     * @param document the document
     * @param offset the offset
     * @param partitioning the partitioning
     * @return the block balance
     */
    private static int getBlockBalance(IDocument document, int offset, String partitioning) {
        if (offset < 1)
            return -1;
        if (offset >= document.getLength())
            return 1;

        int begin= offset;
        int end= offset - 1;

        YangHeuristicScanner scanner= new YangHeuristicScanner(document);

        while (true) {
            begin= scanner.findOpeningPeer(begin - 1, '{', '}');
            end= scanner.findClosingPeer(end + 1, '{', '}');
            if (begin == -1 && end == -1)
                return 0;
            if (begin == -1)
                return -1;
            if (end == -1)
                return 1;
        }
    }    

    // private static IRegion createRegion(ASTNode node, int delta) {
    // return node == null ? null : new Region(node.getStartPosition() + delta, node.getLength());
    // }
    //
    // private static IRegion getToken(IDocument document, IRegion scanRegion, int tokenId) {
    //
    // try {
    //
    // final String source= document.get(scanRegion.getOffset(), scanRegion.getLength());
    //
    // fgScanner.setSource(source.toCharArray());
    //
    // int id= fgScanner.getNextToken();
    // while (id != ITerminalSymbols.TokenNameEOF && id != tokenId)
    // id= fgScanner.getNextToken();
    //
    // if (id == ITerminalSymbols.TokenNameEOF)
    // return null;
    //
    // int tokenOffset= fgScanner.getCurrentTokenStartPosition();
    // int tokenLength= fgScanner.getCurrentTokenEndPosition() + 1 - tokenOffset; // inclusive end
    // return new Region(tokenOffset + scanRegion.getOffset(), tokenLength);
    //
    // } catch (InvalidInputException x) {
    // return null;
    // } catch (BadLocationException x) {
    // return null;
    // }
    // }
    

}