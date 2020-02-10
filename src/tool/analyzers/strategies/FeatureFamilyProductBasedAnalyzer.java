package tool.analyzers.strategies;

import jadd.ADD;
import jadd.JADD;

import java.util.List;
import java.util.stream.Collectors;

import paramwrapper.ParametricModelChecker;
import tool.CyclicRdgException;
import tool.RDGNode;
import tool.analyzers.ADDReliabilityResults;
import tool.analyzers.IPruningStrategy;
import tool.analyzers.IReliabilityAnalysisResults;
import tool.analyzers.NoPruningStrategy;
import tool.analyzers.buildingblocks.AssetProcessor;
import tool.analyzers.buildingblocks.Component;
import tool.analyzers.buildingblocks.ConcurrencyStrategy;
import tool.analyzers.buildingblocks.DerivationFunction;
import tool.analyzers.buildingblocks.FamilyBasedHelper;
import tool.stats.CollectibleTimers;
import tool.stats.IFormulaCollector;
import tool.stats.ITimeCollector;
import expressionsolver.Expression;
import expressionsolver.ExpressionSolver;

/**
 * Orchestrator of feature-family-product-based analyses.
 */
public class FeatureFamilyProductBasedAnalyzer {

    private ExpressionSolver expressionSolver;
    private FeatureBasedFirstPhase firstPhase;
    private ITimeCollector timeCollector;

    public FeatureFamilyProductBasedAnalyzer(JADD jadd,
                                      ADD featureModel,
                                      ParametricModelChecker modelChecker,
                                      ITimeCollector timeCollector,
                                      IFormulaCollector formulaCollector) {
    	this.expressionSolver = new ExpressionSolver(jadd);
    	this.firstPhase = new FeatureBasedFirstPhase(modelChecker, formulaCollector);
    	this.timeCollector = timeCollector;
    }

    public IReliabilityAnalysisResults evaluateReliability(RDGNode node, ConcurrencyStrategy concurrencyStrategy) throws CyclicRdgException {
    	List<RDGNode> dependencies = node.getDependenciesTransitiveClosure();
    	
    	List<Component<String>> expressions = firstPhase.getReliabilityExpressions(dependencies, concurrencyStrategy);
    	
    	/* TODO: Variability encoding */
    	
    	/* TODO: Product-based step */
        return null;
    }
}
