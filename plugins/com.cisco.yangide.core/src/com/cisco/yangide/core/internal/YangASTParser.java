/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package com.cisco.yangide.core.internal;

import java.io.IOException;
import java.io.InputStream;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.eclipse.core.runtime.CoreException;
import org.opendaylight.yangtools.antlrv4.code.gen.YangLexer;
import org.opendaylight.yangtools.antlrv4.code.gen.YangParser;
import org.opendaylight.yangtools.antlrv4.code.gen.YangParser.YangContext;

import com.cisco.yangide.core.dom.Module;

/**
 * @author Konstantin Zaitsev
 * @date Jun 26, 2014
 */
public class YangASTParser {

    public Module parseYangFile(char[] chars) throws IOException, CoreException {
        return parseYangFile(new ANTLRInputStream(chars, chars.length));
    }

    public Module parseYangFile(InputStream stream) throws IOException, CoreException {
        return parseYangFile(new ANTLRInputStream(stream));
    }

    private Module parseYangFile(ANTLRInputStream input) throws IOException, CoreException {
        YangContext yangContext = parseYangSource(input);
        YangParserModelListener modelListener = new YangParserModelListener();
        ParseTreeWalker.DEFAULT.walk(modelListener, yangContext);
        return modelListener.getModule();
    }

    private YangContext parseYangSource(ANTLRInputStream input) throws IOException, CoreException {
        final YangLexer lexer = new YangLexer(input);
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final YangParser parser = new YangParser(tokens);
        // remove any error listener to get possibility parse incompleted file
        parser.removeErrorListeners();
        
        return parser.yang();
    }
}
