package com.cisco.yangide.editor.editors;

import org.eclipse.jdt.ui.text.IColorManager;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WhitespaceRule;

public class YANGTagScanner extends RuleBasedScanner {

	public YANGTagScanner(IColorManager manager) {
		IToken string =
			new Token(
				new TextAttribute(manager.getColor(IYANGColorConstants.YANG_STRING)));

		IRule[] rules = new IRule[3];

		// Add rule for double quotes
		rules[0] = new SingleLineRule("\"", "\"", string, '\\');
		// Add a rule for single quotes
		rules[1] = new SingleLineRule("'", "'", string, '\\');
		// Add generic whitespace rule.
		rules[2] = new WhitespaceRule(new YANGWhitespaceDetector());

		setRules(rules);
	}
}
