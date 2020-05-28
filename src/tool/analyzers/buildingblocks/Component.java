package tool.analyzers.buildingblocks;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jadd.ADD;
import jadd.JADD;

/**
 * Represents a component in an asset base.
 *
 * @param <T> Type of asset.
 */
public class Component<T> {

    private String id;
    private String presenceCondition;
    private T asset;
    private Collection<Component<T>> dependencies;

    public Component(String id, String presenceCondition, T asset) {
        this.id = id;
        this.presenceCondition = presenceCondition;
        this.asset = asset;
        this.dependencies = new HashSet<Component<T>>();
    }

    public Component(String id, String presenceCondition, T asset, Collection<Component<T>> dependencies) {
        this(id, presenceCondition, asset);
        this.dependencies = dependencies;
    }

    public String getId() {
        return id;
    }

    public String getPresenceCondition() {
        return presenceCondition;
    }

    public T getAsset() {
        return asset;
    }

    public Collection<Component<T>> getDependencies() {
        return dependencies;
    }

    /**
     * Maps this Component<T> into a Component<U> by means of a function
     * from T to U.
     *
     * This characterizes Component as a functor.
     *
     * @param mapper
     * @return
     */
    public <U> Component<U> fmap(Function<T, U> mapper) {
        Collection<Component<U>> mappedDependencies = this.getDependencies().stream()
                .map(c -> c.fmap(mapper))
                .collect(Collectors.toSet());
        return new Component<U>(this.getId(),
                                this.getPresenceCondition(),
                                mapper.apply(this.getAsset()),
                                mappedDependencies);
    }

    // TODO Candidate!
    public static <P, A, V> V deriveFromMany(List<Component<A>> dependencies,
                                             DerivationFunction<P, A, V> derive,
                                             IsPresent<A, P> isPresent) {
        Map<String, V> derivedModels = new HashMap<String, V>();
        return dependencies.stream()
                .map(c -> deriveSingle(c, isPresent, derive, derivedModels))
                .reduce((first, actual) -> actual)
                .get();
    }

    // TODO Candidate!
    private static <P, A, V> V deriveSingle(Component<A> component,
                                            IsPresent<A, P> isPresent,
                                            DerivationFunction<P, A, V> derive,
                                            Map<String, V> derivedModels) {
        P presence = isPresent.apply(component);
        V derived = derive.apply(presence, component.getAsset(), derivedModels);
        derivedModels.put(component.getId(), derived);
        return derived;
    }
    
    
    /**
     * @author andrelanna
     * This method has the same role of its deriveFromMany method without jadd parameter.
     * The included jadd parameter was include to allow the persistence of ADDs of each
     * RDG node. However, this is a temporary solution. This method must be changed soon.
     * @param jadd - it contains the variable store and method to dump ADDs.
     * @return
     */
	public static <P, A, V> V deriveFromMany(List<Component<A>> dependencies,
									 DerivationFunction<P, A, V> derive,
									 IsPresent<A, P> isPresent,
									 JADD jadd) {
		Map<String, V> derivedModels = new HashMap<String, V>();
        return dependencies.stream()
                .map(c -> deriveSingle(c, isPresent, derive, derivedModels, jadd))
                .reduce((first, actual) -> actual)
                .get();
	}
	
	/**
     * @author andrelanna
     * This method has the same role of its deriveSingle method without jadd parameter.
     * The included jadd parameter was include to allow the persistence of ADDs of each
     * RDG node. However, this is a temporary solution. This method must be changed soon.
     * @param jadd - it contains the variable store and method to dump ADDs.
     * @return
     */
	private static <P, A, V> V deriveSingle(Component<A> component,
											IsPresent<A, P> isPresent,
											DerivationFunction<P, A, V> derive,
											Map<String, V> derivedModels, 
											JADD jadd) {
		V temporary = checkPersistedADD(component, jadd);
		V derived = null;
		if (temporary != null) {
			derived = temporary;
			System.out.println("FOUND: " + derived);
		} else {
			P presence = isPresent.apply(component);
			derived = derive.apply(presence, component.getAsset(), derivedModels);
			System.out.println("NOT FOUND: " + derived);
			derivedModels.put(component.getId(), derived);
			jadd.dumpADD((ADD)derived, component.getId() + ".dd");
//			jadd.dumpDD(component.getId(), (ADD)derived, component.getId() + ".dd");	
		}
		
		return derived;
	}

	/**
	 * This method looks for an ADD persisted in the application folder and returns 
	 * it in case it exists, null otherwise
	 * @param <A>
	 * @param componentId
	 * @return 
	 * @return - the persisted ADD or null in case it does not exist.
	 */
	@SuppressWarnings("unchecked")
	private static <V, A> V checkPersistedADD(Component<A> component, JADD jadd) {
		String fileName = component.getId() + ".dd";
		File f = new File(fileName);
		
		if (f.exists() && !f.isDirectory()) {
			return (V) jadd.readADD(fileName);
		} else 
			return null;
	}

	
	
}
