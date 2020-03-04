package tool.analyzers.strategies;

import jadd.ADD;
import jadd.JADD;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.logging.Logger;

import paramwrapper.ParametricModelChecker;
import tool.CyclicRdgException;
import tool.RDGNode;
import tool.analyzers.IReliabilityAnalysisResults;
import tool.analyzers.MapBasedReliabilityResults;
import tool.analyzers.buildingblocks.Component;
import tool.analyzers.buildingblocks.ConcurrencyStrategy;
import tool.analyzers.buildingblocks.PresenceConditions;
import tool.analyzers.buildingblocks.ProductIterationHelper;
import tool.stats.CollectibleTimers;
import tool.stats.IFormulaCollector;
import tool.stats.ITimeCollector;
import expressionsolver.Expression;
import expressionsolver.ExpressionSolver;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
/**
 * Orchestrator of feature-family-product-based analyses.
 */
public class FeatureFamilyProductBasedAnalyzer {

    private static final Logger LOGGER = Logger.getLogger(FamilyProductBasedAnalyzer.class.getName());
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
        if (concurrencyStrategy == ConcurrencyStrategy.PARALLEL) {
            LOGGER.info("Solving the family-wide expression for each product in parallel.");
        }
		List<RDGNode> dependencies = node.getDependenciesTransitiveClosure();

		/* feature step */
        timeCollector.startTimer(CollectibleTimers.MODEL_CHECKING_TIME);
		List<Component<String>> components = firstPhase.getReliabilityExpressions(dependencies, concurrencyStrategy);
        timeCollector.stopTimer(CollectibleTimers.MODEL_CHECKING_TIME);

		/* variability encoding */
        timeCollector.startTimer(CollectibleTimers.EXPRESSION_SOLVING_TIME);
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

		Map<Collection<String>, Double> results;

        if (concurrencyStrategy == ConcurrencyStrategy.SEQUENTIAL) {
            Expression<Double> parsedExpression = expressionSolver.parseExpression(expression);
            results = ProductIterationHelper.evaluate(configuration -> evaluateSingle(parsedExpression,
                                                                                      configuration,
                                                                                      eqClassToPC),
                                                      configurations,
                                                      concurrencyStrategy);
        } else {
        	String expressionCopy = expression;
            results = ProductIterationHelper.evaluate(configuration -> evaluateSingle(expressionCopy,
                                                                                      configuration,
                                                                                      eqClassToPC),
                                                      configurations,
                                                      concurrencyStrategy);
        }
		
        LOGGER.info("Formulae evaluation ok...");
        timeCollector.startTimer(CollectibleTimers.EXPRESSION_SOLVING_TIME);
		return new MapBasedReliabilityResults(results);
	}

	private String replaceVariables(String exp, Map<String,String> varToPC, Map<String,String> pcEquiv) {
		List<String> vars = varToPC.keySet().stream().collect(Collectors.toList());
		
		for (String var : vars) {
			String pc = varToPC.get(var);
			String newVar = pcEquiv.get(pc);
			exp = substitute(var, newVar, exp);
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
				exp = substitute(varSubs, var2ite.get(varSubs), exp);
			}

			String ite = getITE(var, exp, "1.0");
			var2ite.put(var, ite);
		}

		String rootVar = variables.get(n-1);
		String iteExpression = var2ite.get(rootVar);
		return iteExpression;
	}

	private String getITE(String var, String exp, String alt) {
		return String.format("(((%s)*(%s)) + ((1-%s)*(%s)))", var, exp, var, alt);
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

    private Double evaluateSingle(String expression, Collection<String> configuration, Map<String, String> eqClassToPC) {
        Expression<Double> parsedExpression = expressionSolver.parseExpression(expression);
        return evaluateSingle(parsedExpression, configuration, eqClassToPC);
    }

	public static String substitute(String var, String subs, String exp) {
		String newExp = exp.replaceAll("\\b"+var+"\\b", subs);
		return newExp;
	}
}