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
import tool.analyzers.buildingblocks.PresenceConditions;
import tool.analyzers.buildingblocks.ProductIterationHelper;
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
import java.util.function.Function;
import java.lang.Runtime;
/**
 * Orchestrator of feature-family-product-based analyses.
 */
public class FeatureFamilyProductBasedAnalyzer {

	private ExpressionSolver expressionSolver;
	private FeatureBasedFirstPhase firstPhase;
	private ITimeCollector timeCollector;
	private IFormulaCollector formulaCollector;

	public FeatureFamilyProductBasedAnalyzer(JADD jadd,
			ADD featureModel,
			ParametricModelChecker modelChecker,
			ITimeCollector timeCollector,
			IFormulaCollector formulaCollector) {
		this.expressionSolver = new ExpressionSolver(jadd);
		this.firstPhase = new FeatureBasedFirstPhase(modelChecker, formulaCollector);
		this.timeCollector = timeCollector;
		this.formulaCollector = formulaCollector;
	}

	public IReliabilityAnalysisResults evaluateReliability(RDGNode node, Stream<Collection<String>> configurations, ConcurrencyStrategy concurrencyStrategy) throws CyclicRdgException {
		List<RDGNode> dependencies = node.getDependenciesTransitiveClosure();

		/* feature step */
		List<Component<String>> components = firstPhase.getReliabilityExpressions(dependencies, concurrencyStrategy);

		/* variability encoding */
		String expression = getExpression(dependencies, components);

		/* iterate products */
		formulaCollector.collectFormula(node, expression);

		List<String> presenceConditions = dependencies.stream()
				.map(RDGNode::getPresenceCondition)
				.collect(Collectors.toList());
		Map<String, String> pcEquivalence = PresenceConditions.toEquivalenceClasses(presenceConditions);
		Map<String, String> eqClassToPC = pcEquivalence.entrySet().stream()
				.collect(Collectors.toMap(e -> e.getValue(),
						e -> e.getKey(),
						(a, b) -> a));
		Map<String,String> varToPC = components
				                     .stream()
				                     .map(e -> new String[]{e.getId(), e.getPresenceCondition()})
				                     .collect(Collectors.toMap(x -> x[0], x -> x[1]));
		
		expression = replaceVariables(expression, varToPC, pcEquivalence);

		Expression<Double> parsedExpression = expressionSolver.parseExpression(expression);

		Map<Collection<String>, Double> results = 
			ProductIterationHelper.evaluate(configuration -> evaluateSingle(parsedExpression,
				configuration,
				eqClassToPC),
				configurations,
				concurrencyStrategy);
		return new MapBasedReliabilityResults(results);
	}

	private String replaceVariables(String exp, Map<String,String> varToPC, Map<String,String> pcEquiv) {
		List<String> vars = varToPC.keySet().stream().collect(Collectors.toList());
		
		for (String var : vars) {
			String pc = varToPC.get(var);
			String newVar = pcEquiv.get(pc);
			exp = preprocessExpression(exp);
			exp = substitute(var, newVar, exp);
			exp = postprocessExpression(exp);
		}
		
		return exp;
	}
	
	private String getExpression(List<RDGNode> dependencies, List<Component<String>> components) {
		int n = components.size();
		List<String> variables = dependencies.stream().map(e -> e.getId()).collect(Collectors.toList());
		List<String> expressions = components.stream().map(e -> e.getAsset()).collect(Collectors.toList());
		Map<String, String> var2ite = new HashMap<String, String>();

		/* expression variability encoding */
		for (int i = 0; i < n; i++) {
			String var = variables.get(i);
			String exp = expressions.get(i);


			for (int j = 0; j < i; j++) {
				String varSubs = variables.get(j);
				exp = preprocessExpression(exp);
				exp = substitute(varSubs, var2ite.get(varSubs), exp);
				exp = postprocessExpression(exp);
			}

			String ite = getITE(var, exp, "1.0");
			ite = postprocessExpression(ite);
			var2ite.put(var, ite);
		}

		String rootVar = variables.get(n-1);
		String iteExpression = var2ite.get(rootVar);
		return iteExpression;
	}

	private String getITE(String var, String exp, String alt) {
		return String.format("(((%s) * (%s)) + ((1-%s)*(%s)))", var, exp, var, alt);
	}

	private Double evaluateSingle(Expression<Double> expression, Collection<String> configuration, Map<String, String> eqClassToPC) {
		Function<Map.Entry<String, String>, Boolean> isPresent = e -> PresenceConditions.isPresent(e.getValue(),
				configuration,
				expressionSolver);
		Map<String, Double> values = eqClassToPC.entrySet().stream()
				.collect(Collectors.toMap(e -> e.getKey(),
						isPresent.andThen(present -> present ? 1.0 : 0.0)));

		return expression.solve(values);

	}

	private String substitute(String var, String subs, String exp) {
		String newExp = exp.replaceAll(" " + var + " ", "(" + subs + ")");
		return newExp;
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
	
	private String postprocessExpression(String exp) {
		exp = exp.replaceAll(" ", "");
		return exp;
	}
}