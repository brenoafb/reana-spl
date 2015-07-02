package Modeling.SequenceDiagrams;

import java.util.ArrayList;

import Modeling.Node;
import Modeling.UnsupportedFragmentTypeException;

public class Fragment extends Node {
	
	// Atributos
		private String name;
		private FragmentType type;
		private ArrayList<Node> nodes;
		private ArrayList<Lifeline> lifelines;
	
	// Construtores
		public Fragment(String id) {
			super(id);
			name = "";
			type = null;
			nodes = new ArrayList<Node>();
			lifelines = new ArrayList<Lifeline>();
		}
		
		public Fragment(String id, FragmentType type) {
			this(id);
			this.type = type;
		}
		
		public Fragment(String id, String name) {
			this(id);
			this.name = name;
		}
		
		public Fragment(String id, String typeName, String name) throws UnsupportedFragmentTypeException {
			this(id, FragmentType.getType(typeName));
			this.name = name;
		}
		
	// Métodos relevantes
		public void setType(String typeName) throws UnsupportedFragmentTypeException{
			if (typeName.equals("opt")) {
				this.setType(FragmentType.optional);
			} else if (typeName.equals("alt")) {
				this.setType(FragmentType.alternative);
			}else if (typeName.equals("loop")) {
				this.setType(FragmentType.loop);
			} else {
				throw new UnsupportedFragmentTypeException("Fragment of type " + typeName + " is not supported!");
			}
		}
		
		public void addLifeline(Lifeline toAdd) {
			lifelines.add(toAdd);
		}
		
		public void addNode(Node toAdd) {
			nodes.add(toAdd);
		} 
		
		public void print() {
			super.print();
		}
		
	// Getters e Setters
		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public FragmentType getType() {
			return type;
		}

		public void setType(FragmentType type) {
			this.type = type;
		}

		public ArrayList<Node> getNodes() {
			return nodes;
		}

		public void setNodes(ArrayList<Node> nodes) {
			this.nodes = nodes;
		}

		public ArrayList<Lifeline> getLifelines() {
			return lifelines;
		}

		public void setLifelines(ArrayList<Lifeline> lifelines) {
			this.lifelines = lifelines;
		}
}
