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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.lang.Runtime;
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
    	List<String> variables = dependencies.stream().map(e -> e.getId()).collect(Collectors.toList());
    	List<String> expressions = components.stream().map(e -> e.getAsset()).collect(Collectors.toList());
    	List<String> pcs = components.stream().map(e -> e.getPresenceCondition()).collect(Collectors.toList());
    	Map<String,String> var2exp = new HashMap<String,String>();
    	Map<String, String> var2pc = new HashMap<String, String>();
    	Map<String, String> var2ite = new HashMap<String, String>();
    	
    	/* expression variability encoding */
		for (int i = 0; i < n; i++) {
			String var = variables.get(i);
			String exp = expressions.get(i);
			String pc = pcs.get(i);
			
			exp = preprocessExpression(exp);
    		
			var2exp.put(var, exp);
			var2pc.put(var, pc);
			
			for (int j = 0; j < i; j++) {
				String varSubs = variables.get(j);
				exp = exp.replaceAll(varSubs + " ", var2ite.get(varSubs));
			}
			
			var2ite.put(var, getITE(var, exp));
		}

		String rootVar = variables.get(n-1);
		String iteExpression = var2ite.get(rootVar);
		
    	/* product iteration step */
    	Map<Collection<String>, Double> results = new HashMap<Collection<String>, Double>();
    	List<Collection<String>> configList = configurations.collect(Collectors.toList());
    	for (Collection<String> config : configList) {
    		Map <String, Double> var2value = new HashMap<String, Double>();
    		for (String var : config) {
    			var2value.put(var, 1.0);
    		}
    		for (String var : variables) {
    			String pc = var2pc.get(var);
    			if (pc == "true") {
    				var2value.put(var, 1.0);
    			} else if (var2value.containsKey(pc)) {
    				var2value.put(var, var2value.get(pc));
    			} else {
    				var2value.put(var, 0.0);
    			}
    		}

    		JEP parser = new JEP();
    		variables.forEach(v -> parser.addVariable(v, var2value.get(v)));
    		parser.parseExpression(iteExpression);
    		Double reliability = parser.getValue();
    		if (parser.hasError()) {
    			System.out.println("Parser error: " + parser.getErrorInfo());
    		}
    		results.put(config, reliability);
    	}
    	
    	// call program

        return new MapBasedReliabilityResults(results);
    }
    
    private String preprocessExpression(String exp) {
    	exp = exp.replaceAll("\\(", " ( ");
    	exp = exp.replaceAll("\\)", " ) ");
    	exp = exp.replaceAll("\\+", " + ");
    	exp = exp.replaceAll("\\-", " - ");
    	exp = exp.replaceAll("\\*", " * ");
    	exp = exp.replaceAll("\\/", " / ");
    	return exp;
    }

    private String getITE(String var, String exp) {
    	return " ( " + var + " * ( " + exp + " ) + ( 1 - " + var + " ) ) ";
    }
}
