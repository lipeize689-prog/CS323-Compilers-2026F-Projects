package framework.project2;

import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * Project 2 TODO.
 *
 * ANTLR's DefaultErrorStrategy is still responsible for the actual recovery
 * algorithm. Do NOT reimplement recoverInline(), single-token insertion, or
 * single-token deletion.
 *
 * Your task is to adapt ANTLR's missing-token and unwanted-token reports to
 * ErrorReporter using the line-number rules defined in the Project 2 spec.
 */
public final class Project2ErrorStrategy extends DefaultErrorStrategy {

    private final ErrorReporter reporter;

    public Project2ErrorStrategy(ErrorReporter reporter) {
        this.reporter = reporter;
    }

    @Override
    protected void reportMissingToken(Parser recognizer) {
        if (inErrorRecoveryMode(recognizer)) {
            return;
        }


        beginErrorCondition(recognizer);
        Token currentToken = recognizer.getCurrentToken();
        int tokenType = recognizer.getExpectedTokens().getMinElement();
        int line = currentToken.getLine() - 1;

        reporter.reportMissing(tokenType, line);
        /*
         * TODO:
         * Report the missing symbol through ErrorReporter.
         *
         * You need to determine:
         * 1. which token is missing;
         * 2. which line should be reported.
         */
    }

    @Override
    protected void reportUnwantedToken(Parser recognizer) {
        if (inErrorRecoveryMode(recognizer)) {
            return;
        }

        beginErrorCondition(recognizer);
        Token currentToken = recognizer.getCurrentToken();
        int tokenType = currentToken.getType();
        int line = currentToken.getLine() - 1;

        reporter.reportExtraneous(tokenType, line);
        /*
         * TODO:
         * Report the extraneous symbol through ErrorReporter.
         *
         * You need to determine:
         * 1. which token is extraneous;
         * 2. which line should be reported.
         */
    }
}
