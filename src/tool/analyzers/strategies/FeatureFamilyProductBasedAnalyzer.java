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
    	// START
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

  		PrintWriter writer;
		try {
			writer = new PrintWriter("/home/breno/tmp/formulas.txt", "UTF-8");
			for (int i = 0; i < n; i++) {
				String var = variables.get(i);
				String exp = expressions.get(i);
				String pc = pcs.get(i);
    		
				var2exp.put(var, exp);
				var2pc.put(var, pc);
    		
				writer.println(var + "\n" + exp);
			}
			writer.close();
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
        
    	/* expression variability encoding */

    	/* product iteration step */
    	int i = 0;
    	Map<Collection<String>, Double> results = new HashMap<Collection<String>, Double>();
    	List<Collection<String>> configList = configurations.collect(Collectors.toList());
    	List<String> paths = new LinkedList<String>();
    	for (Collection<String> config : configList) {
    		Map <String, Integer> var2value = new HashMap<String, Integer>();
    		for (String var : config) {
    			var2value.put(var, 1);
    		}
    		for (String var : variables) {
    			String pc = var2pc.get(var);
    			if (pc == "true") {
    				var2value.put(var, 1);
    			} else if (var2value.containsKey(pc)) {
    				var2value.put(var, var2value.get(pc));
    			} else {
    				var2value.put(var, 0);
    			}
    		}

    		PrintWriter valueWriter;
			try {
				String path = "/home/breno/tmp/values-" + i + ".txt";
				valueWriter = new PrintWriter(path, "UTF-8");
				variables.forEach(v -> valueWriter.println(v + "\n" + var2value.get(v)));
				valueWriter.close();
				paths.add(path);
			} catch (FileNotFoundException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (UnsupportedEncodingException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
    		i++;
    	}
    	
    	// call program
    	String command = "/home/breno/Documents/formula-parser/TestLang /home/breno/tmp/formulas.txt";
    	for (String path : paths) {
    		command += " " + path;
    	}

    	command += " /home/breno/tmp/output.txt";
    	
    	Runtime rt = Runtime.getRuntime();
    	try {
			Process program = rt.exec(command);
			int exitCode = program.waitFor();
			System.out.println("Exit code: " + exitCode);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

    	// TODO read results
        return new MapBasedReliabilityResults(results);
    }
}
