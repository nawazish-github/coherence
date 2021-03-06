/*
 * Copyright (c) 2000, 2020, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 * http://oss.oracle.com/licenses/upl.
 */
package com.tangosol.coherence.dslquery.token.persistence;

import com.tangosol.coherence.dslquery.token.SQLOPToken;
import com.tangosol.coherence.dsltools.precedence.OPException;
import com.tangosol.coherence.dsltools.precedence.OPParser;
import com.tangosol.coherence.dsltools.precedence.OPScanner;
import com.tangosol.coherence.dsltools.termtrees.AtomicTerm;
import com.tangosol.coherence.dsltools.termtrees.Term;
import com.tangosol.coherence.dsltools.termtrees.Terms;

/**
 * SQLResumeServiceOPToken is used for parsing and specifying the AST
 * used for resuming services.
 * <p>
 * Syntax:
 * <p>
 * RESUME SERVICE 'service'
 *
 * @author tam  2014.08.05
 * @since 12.2.1
 */
public class SQLResumeServiceOPToken
        extends SQLOPToken
    {
    // ----- constructors --------------------------------------------------

    /**
     * Construct a new SQLResumeServiceOPToken.
     *
     * @param id the string representing the command
     */
    public SQLResumeServiceOPToken(String id)
        {
        super(id, IDENTIFIER_NODE);
        }

    // ----- Operator Precedence API ----------------------------------------

    @Override
    public Term nud(OPParser p)
        {
        OPScanner s = p.getScanner();

        if (s.advanceWhenMatching("service"))
            {
            Term termService = Terms.newTerm("service", AtomicTerm.createString(s.getCurrentAsStringWithAdvance()));

            return Terms.newTerm(FUNCTOR, termService);
            }
        else
            {
            throw new OPException("Expected SERVICE but found " + s.getCurrentAsString());
            }
        }

    // ----- constants ------------------------------------------------------

    /**
     * The functor name used to represent this node in an AST
     */
    public static final String FUNCTOR = "sqlResumeService";
    }
