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
import org.nfunk.jep.JEP;
import org.nfunk.jep.Node;

import java.util.HashMap;
import java.util.Map;
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
    	
    	List<Component<String>> components = firstPhase.getReliabilityExpressions(dependencies, concurrencyStrategy);
    	
    	int n = components.size();
    	List<String> variables = components.stream().map(e -> e.getId()).collect(Collectors.toList());
    	// List<String> variables = components.stream().map(e -> e.getId()).collect(Collectors.toList());
    	List<String> expressions = components.stream().map(e -> e.getAsset()).collect(Collectors.toList());
    	List<String> pcs = components.stream().map(e -> e.getPresenceCondition()).collect(Collectors.toList());
    	Map<String,String> var2exp = new HashMap<String,String>();
    	Map<String, String> var2pc = new HashMap<String, String>();
    	Map<String,Double> var2val = new HashMap<String, Double>();
    	Map<String,String> var2ite = new HashMap<String,String>();

    	for (int i = 0; i < n; i++) {
    		var2exp.put(variables.get(i), expressions.get(i));
    		var2pc.put(variables.get(i), pcs.get(i));
    	}

    	JEP parser = new JEP();
    	JEP parserStr = new JEP();

    	for (String variable : variables) {
    		System.out.println("Adding variable " + variable + " as " + var2exp.get(variable));
    		parser.parseExpression(var2exp.get(variable));
    		Double value = parser.getValue();
    		var2val.put(variable, value);
    		parser.addVariableAsObject(variable, value);
    		
    	}
        
    	/* get the ite expressions */
    	for (int i = 0; i < n; i++) {
    		String var = variables.get(i);
    		String exp = var2exp.get(var);
    		
    		for (int j = 0; j < i; j++) {
    			String varPrev = variables.get(j);
    			String newExp = exp.replace(varPrev, "(" + var2ite.get(varPrev) + ")");
    			exp = newExp;
    		}
    		
    		String ite = getITEExpression(var, exp);
    		var2ite.put(var, ite);
    	}
    	System.out.println("parsing expression " + var2exp.get("BSN"));
    	parser.parseExpression("BSN");
    	Node p = parser.getTopNode();
    	System.out.println(parser.getValueAsObject());
    	System.out.println(parser.getErrorInfo());

    	System.out.println(var2ite.get("BSN"));

    	/* TODO: Variability encoding */
    	/* TODO: Product-based step */
        return null;
    }
    
    private String getITEExpression(String variable, String expression) {
    	return variable + "*(" + expression + ") + (1-" + variable + ")";
    }
}
