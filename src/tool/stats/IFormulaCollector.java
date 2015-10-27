package tool.stats;

import java.io.PrintStream;

/**
 * Collects formulas in order to generate stats.
 * @author thiago
 *
 */
public interface IFormulaCollector {

    public void collectFormula(String formula);
    public long getMinFormulaSize();
    public long getMaxFormulaSize();
    public long getSizesSum();

    public void printStats(PrintStream out);

}