package ac.soton.eventb.emf.diagrams.importExport.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EPackage;

import ac.soton.eventb.emf.diagrams.importExport.IPorterAdapter;
import ac.soton.eventb.emf.diagrams.importExport.IRule;

public class ImporterConfig{
	public String generatorID;
	public EPackage rootSourcePackage;
	public EClassifier rootSourceClass;
	public Map<EClassifier, List<IRule>> ruleMapping;
	public IPorterAdapter adapter;
	
	public ImporterConfig(String generatorID, EPackage rootSourcePackage, EClassifier rootSourceClass, IPorterAdapter adapter){
		this.generatorID = generatorID;
		this.rootSourcePackage = rootSourcePackage;
		this.rootSourceClass = rootSourceClass;
		this.adapter = adapter;
		ruleMapping = new HashMap<EClassifier, List<IRule>>();
	}
	
	public void addRule(EClassifier sourceClass,IRule rule){
		List<IRule> ruleList = ruleMapping.get(sourceClass);
		if (ruleList == null) ruleList = new ArrayList<IRule>();
		ruleList.add(rule);
		ruleMapping.put(sourceClass, ruleList);
	}
	
}