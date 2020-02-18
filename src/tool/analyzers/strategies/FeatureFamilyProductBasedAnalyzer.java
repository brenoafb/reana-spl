package tool.analyzers.strategies;

import jadd.ADD;
import jadd.JADD;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import paramwrapper.ParametricModelChecker;
import tool.CyclicRdgException;
import tool.RDGNode;
import tool.analyzers.ADDReliabilityResults;
import tool.analyzers.IPruningStrategy;
import tool.analyzers.IReliabilityAnalysisResults;
import tool.analyzers.MapBasedReliabilityResults;
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
import org.nfunk.jep.type.DoubleNumberFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
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

    public IReliabilityAnalysisResults evaluateReliability(RDGNode node, Stream<Collection<String>> configurations, ConcurrencyStrategy concurrencyStrategy) throws CyclicRdgException {
    	List<RDGNode> dependencies = node.getDependenciesTransitiveClosure();
    	
    	/* feature step */
    	List<Component<String>> components = firstPhase.getReliabilityExpressions(dependencies, concurrencyStrategy);
    	
    	/* preparing variables */
    	int n = components.size();
    	List<String> variables = components.stream().map(e -> e.getId()).collect(Collectors.toList());
    	List<String> expressions = components.stream().map(e -> e.getAsset()).collect(Collectors.toList());
    	List<String> pcs = components.stream().map(e -> e.getPresenceCondition()).collect(Collectors.toList());
    	Map<String,String> var2exp = new HashMap<String,String>();
    	Map<String, String> var2pc = new HashMap<String, String>();
    	Map<String,String> var2ite = new HashMap<String,String>();

    	for (int i = 0; i < n; i++) {
    		var2exp.put(variables.get(i), expressions.get(i));
    		var2pc.put(variables.get(i), pcs.get(i));
    	}
        
    	/* expression variability encoding */
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
    	
    	/* this is the single expression that encodes the reliability of the product line */
    	String iteExpression = var2ite.get(variables.get(n-1));

    	/* product iteration step */
    	Map<Collection<String>, Double> results = new HashMap<Collection<String>, Double>();
    	for (Collection<String> config : configurations.collect(Collectors.toList())) {
    		Map <String, Double> var2double = new HashMap<String, Double>();
    		for (String var : config) {
    			var2double.put(var, 1.0);
    		}
    		for (String var : variables) {
    			String pc = var2pc.get(var);
    			if (pc == "true") {
    				var2double.put(var, 1.0);
    			} else if (var2double.containsKey(pc)) {
    				var2double.put(var, var2double.get(pc));
    			} else {
    				var2double.put(var, 0.0);
    			}
    		}
    		
    		JEP parser1 = new JEP();
    		parser1.setImplicitMul(true);
    		variables.forEach(v -> parser1.addVariable(v, var2double.get(v)));
    		parser1.parseExpression(iteExpression);
    		Double reliability = parser1.getValue();
    		results.put(config, reliability);
    	}
        return new MapBasedReliabilityResults(results);
    }
    
    private String getITEExpression(String variable, String expression) {
    	return variable + "*(" + expression + ") + (1-" + variable + ")";
    }
}
