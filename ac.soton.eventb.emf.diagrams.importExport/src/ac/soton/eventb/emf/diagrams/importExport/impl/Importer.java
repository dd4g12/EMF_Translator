/**
 * Copyright (c) 2012 University of Southampton.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package ac.soton.eventb.emf.diagrams.importExport.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.edit.domain.EditingDomain;
import org.eclipse.emf.transaction.TransactionalEditingDomain;

import ac.soton.eventb.emf.diagrams.importExport.Activator;
import ac.soton.eventb.emf.diagrams.importExport.GenerationDescriptor;
import ac.soton.eventb.emf.diagrams.importExport.IRule;
import ac.soton.eventb.emf.diagrams.importExport.utils.Make;

/**
 * A generic Importer which is configured from rule classes which have been declared in an extension point.
 * The rules are fired (if enabled) and return a collection of descriptors for adding elements to the model
 * or adding references within the model.
 * Rules may be deferred until later if they depend on other rules which have not yet fired.
 * Descriptors may be prioritised to influence the order of elements in collections. 
 * 
 * @author cfs
 *
 */
public class Importer {
	
	//	configuration data for this instance of a generator
	private ImporterConfig importerConfig;

	
	// VARIABLE DATA
	/*
	 * These mappings are populated from the generation descriptors prior to updating the model
	 * priorities maps from a range of priority numbers (10 down to -10) to a list of new child elements for each priority
	 * parents maps each new element to the parent element that should own it
	 * features maps each new element to the feature of the parent in which it should be contained
	 * 
	 */
	private Map<Integer,List<GenerationDescriptor>> priorities = new HashMap<Integer,List<GenerationDescriptor>>();
	//private Map<EventBObject, EventBObject> trace = new HashMap<EventBObject, EventBObject>();	
	private List<GenerationDescriptor> generatedElements = new ArrayList<GenerationDescriptor>();
	
	
/**
 * Construct a generator. This should only be called from the ImporterFactory.
 * 	
 * @param importerConfig	- the configuration for the generator
 */
	
	public Importer(ImporterConfig importerConfig){ 	
			this.importerConfig = importerConfig;
	}

	
/**
 * generate - this should be called from a command handler action, passing the selected element
 * @param editingDomain 
 * @param sourceElement 
 */
	public List<Resource> generate (TransactionalEditingDomain editingDomain, final EObject sourceElement){
		String generatedByID;
		List<Resource> modifiedResources = new ArrayList<Resource>();
		try {
			
			//check we have a valid configuration for the generator
			if (importerConfig==null) {
				Activator.logError(Messages.GENERATOR_MSG_01(sourceElement));
				return null;
			}
			
			//check we have the correct generator configuration for the source element
			if (sourceElement.eClass() != importerConfig.rootSourceClass){
				Activator.logError(Messages.GENERATOR_MSG_02(sourceElement));
				return null;
			}
			
			//Obtain the extension ID from the source element
			generatedByID = Make.generatedById((sourceElement));
	//		assert(generatedByID != null && generatedByID.startsWith(importerConfig.generatorID)) : "source elements generator ID and generator config do not match";
			
			//do the generation
			doGenerate(sourceElement);
			
			//verifyDescriptors;
			for (GenerationDescriptor generationDescriptor : generatedElements){
				if (generationDescriptor.feature!=null &&
						!generationDescriptor.feature.getEType().isInstance(generationDescriptor.value)){
					Activator.logError(Messages.GENERATOR_MSG_21(generationDescriptor.value,generationDescriptor.feature));
					return null;
				}
				if (generationDescriptor.parent!=null &&
					!generationDescriptor.parent.eClass().getEAllStructuralFeatures().contains(generationDescriptor.feature)){
					Activator.logError(Messages.GENERATOR_MSG_22(generationDescriptor.parent, generationDescriptor.feature));
					return null;
				}
			}

//			DeleteGeneratedCommand deleteGeneratedCommand = new DeleteGeneratedCommand(editingDomain, sourceElement);
//			if (deleteGeneratedCommand.canExecute()){
//				deleteGeneratedCommand.execute(null, null);
//			}else{
//				Activator.logError(Messages.GENERATOR_MSG_03);
//				return null;
//			}
			GeneratedRemover genRemover = new GeneratedRemover(modifiedResources, generatedByID);
			modifiedResources.addAll(genRemover.removeGenerated());
			
			//create new EventB components
			modifiedResources.addAll(createNewComponents(editingDomain, sourceElement, generatedByID));
			
		} catch (Exception e) {
			Activator.logError(e.getMessage(),e);
			return null;
		} 

		//place the newly generated elements in their correct parent features
		//(this is done in a separate post-generation phase and only if the deletion of old generated elements succeeds. 
		// This is so that we do not leave the model in an inconsistent state if the generation fails)
		try {
			modifiedResources.addAll(
					placeGenerated(editingDomain, generatedByID)
					);
			//removeComponents(editingDomain, sourceElement);
		} catch (Exception e) {
			Activator.logError(Messages.GENERATOR_MSG_04, e);
			return null;
		}
		
		//modifiedResources.add(sourceElement.eContainer().eResource()); // NOT FOR IMPORT
		
		return modifiedResources;	
	}
	
//	/**
//	 * Removes Event-B components according to the descriptors
//	 * 
//	 * 
//	 * @param editingDomain
//	 * @param sourceElement
//	 * @throws IOException 
//	 */
//	private void removeComponents(TransactionalEditingDomain editingDomain, EObject sourceElement) throws IOException{
//		String projectName = EcoreUtil.getURI(sourceElement).trimFragment().trimSegments(1).lastSegment();
//		URI projectUri = URI.createPlatformResourceURI(projectName, true);
//		for (GenerationDescriptor generationDescriptor : generatedElements){
//			if (generationDescriptor.feature == CorePackage.Literals.PROJECT__COMPONENTS &&
//					generationDescriptor.value instanceof EventBNamedCommentedComponentElement){
//				String fileName = ((EventBNamedCommentedComponentElement)generationDescriptor.value).getName();
//				String ext = generationDescriptor.value instanceof Context? "buc" :  "bum";
//				URI fileUri = projectUri.appendSegment(fileName).appendFileExtension(ext); //$NON-NLS-1$
//				if(generationDescriptor.remove == true){	
//					Resource oldResource = editingDomain.getResourceSet().getResource(fileUri, false);
//					if(oldResource != null) {
//						oldResource.delete(Collections.EMPTY_MAP);
//					}	
//				}
//			}
//		}
//	}

/*
 * If any generated elements are a new Resource component (e.g. machine, context) this creates a new resource
 * for them in the editing domains resource set and attaches the new element as the content of the resource.
 * Note that we do not save the resource yet in case the generation process does not complete. 
 * 
 * N.B. CURRENTLY ALL RESOURCES ARE ASSUMED TO BE WITHIN THE SAME PROJECT AS THE SOURCE ELEMENT. 
 * (I.E. CURRENTLY WE DO NOT USE generationDecriptor.parent WHICH CAN BE LEFT NULL)
 * 
 * @param editingDomain
 * @param sourceElement
 * @return list of new Resources
 */
	private Collection<? extends Resource> createNewComponents(TransactionalEditingDomain editingDomain, EObject sourceElement, String generatedByID) throws IOException {
		List<Resource> newResources = new ArrayList<Resource>();

		for (GenerationDescriptor generationDescriptor : generatedElements){
			URI fileUri = importerConfig.adapter.getComponentURI(generationDescriptor, sourceElement);
			if (fileUri!=null){
				importerConfig.adapter.setGeneratedBy(generatedByID, generationDescriptor.value);
				importerConfig.adapter.setPriority(0, generationDescriptor.value);
				Resource newResource = editingDomain.createResource(fileUri.toString());
				newResource.getContents().add((EObject)generationDescriptor.value);
				newResources.add(newResource);		
			}
		}
		return newResources;
	}

		
/*
 * puts the generated elements into the model
 * @param editingDomain 
 * @return modified resources
 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Collection<? extends Resource> placeGenerated(EditingDomain editingDomain, String generatedByID) throws Exception {
		//arrange the generation descriptors into priority order
		for (GenerationDescriptor generationDescriptor : generatedElements){
			Integer pri = generationDescriptor.priority;
			List<GenerationDescriptor> objects = priorities.get(pri);
			if (objects ==null) objects = new ArrayList<GenerationDescriptor>();
			objects.add(generationDescriptor); //newChild);
			priorities.put(pri,objects);
		}
		//process the prioritised mappings of generation descriptors
		List<Resource> modifiedResources = new ArrayList<Resource>();
		Map<EList,Integer> positions = new HashMap<EList,Integer>();
		for (int pri=10; pri>=-10; pri--){
			positions.clear();
			if (priorities.containsKey(pri)){
				for (GenerationDescriptor generationDescriptor : priorities.get(pri)){

					if (generationDescriptor.remove == false && importerConfig.adapter.filter(generationDescriptor)){
						continue;								
					}
					Resource resource = null;
					Object value = generationDescriptor.value;
					if (generationDescriptor.parent != null && 
						generationDescriptor.parent.eClass().getEAllStructuralFeatures().contains(generationDescriptor.feature) &&
						generationDescriptor.feature.getEType().isInstance(value)){
						
						Object featureValue = generationDescriptor.parent.eGet(generationDescriptor.feature);
	
						if (featureValue instanceof EList){	
							if(generationDescriptor.remove == false){											
								importerConfig.adapter.setGeneratedBy(generatedByID, generationDescriptor.value);
								importerConfig.adapter.setPriority(pri, generationDescriptor.value);
								//add the new value to the list at the correct index - i.e. after any higher priority elements and
								//after stuff generated by earlier extensions which has the same priority
								int pos = 0;
//								for (int i=0; i<((EList)featureValue).size(); i++){
//									Object v = ((EList)featureValue).get(i);
//									if(v instanceof EventBElement){
//										Attribute at = ((EventBElement)v).getAttributes().get(AttributeIdentifiers.GENERATOR_ID_KEY);
//										String gb = (String) (at==null? null : at.getValue());
//										Integer od = extensionOrder.get(gb);
//										if (od==null) od = extensionOrder.size(); // not an extension => user entered stuff comes last
//										at = ((EventBElement)v).getAttributes().get(AttributeIdentifiers.PRIORITY_KEY);
//										Integer pr = (Integer) (at==null? null : at.getValue());
//										if (pr==null) pr = 0; // no priority => user stuff at priority 0
//										//priority order = highest 1..10,0,-1..-10
//										Integer exo = extensionOrder.containsKey(generatedByID)? extensionOrder.get(generatedByID) : 0;
//										if ((pr>0 && pr < pri) || (pr < 1 && pr > pri) || (pr==pri && od<exo)){
//											pos = i+1;
//										};
//										
//									}
//								}
								
								//TODO: better positioning neeeded???
								//for now put at end of list
								pos = ((EList)featureValue).size();

								((EList)featureValue).add(pos, generationDescriptor.value);
										
							}
							else{
								ArrayList<Object> toRemove = new ArrayList<Object>();
								for(Object obj : (EList)featureValue){
									if(importerConfig.adapter.match(obj, generationDescriptor.value))
										toRemove.add(obj);
								}
								((EList)featureValue).removeAll(toRemove);
							}
						}else {
							if(generationDescriptor.remove == false){
								//FIXME: this should be analysed more
								generationDescriptor.parent.eSet(generationDescriptor.feature, generationDescriptor.value);
							}
							else
								if  (generationDescriptor.feature.isUnsettable())
									generationDescriptor.parent.eUnset(generationDescriptor.feature);
								else
									generationDescriptor.parent.eSet(generationDescriptor.feature, generationDescriptor.feature.getDefaultValue());
						}
						
						//add to list of modifiedResources if not already there
						resource = generationDescriptor.parent.eResource();
					}else{
						//Error messages are generated elsewhere - should not get here.
					}
					if (resource!= null && !modifiedResources.contains(resource)){
						modifiedResources.add(resource);
					}
				}
			}
		}
		return modifiedResources;
	}


	/*
	 * a record of rules that have been deferred
	 */
	private Map<EObject, List<IRule>> deferredRules = new HashMap<EObject,List<IRule>>();
	private void defer(EObject sourceElement2, IRule rule){
		List<IRule> rules = deferredRules.get(sourceElement2);
		if (rules == null) rules = new ArrayList<IRule>();
		rules.add(rule);
		deferredRules.put(sourceElement2, rules);
	}
	
	/*
	 * The generation is done in two stage:
	 * 1) traverse the model firing any appropriate rules that are enabled. This may result in deferred
	 *    rules that are enabled but cannot be fired due to dependencies on other rules.
	 * 2) repeatedly iterate through the deferred rules firing any that can fire until none are left or
	 *    if no progress is made raise an exception.
	 * 
	 * @param sourceElement
	 * @throws Exception - for any exception, abort
	 */
	private void doGenerate(final EObject sourceElement2) throws Exception {
		boolean late = false;
		// stage 1 - traverse the model firing appropriate enabled rules
		traverseModel(sourceElement2);
		
		// stage 2 - deal with any deferred rules
		while (deferredRules.size() >0 ){
			boolean progress = false;
			List<EObject> empties = new ArrayList<EObject>();
			for (EObject sourceElement : deferredRules.keySet()){
				List<IRule> firedRules = new ArrayList<IRule>();
				for (IRule rule : deferredRules.get(sourceElement)){
					if (rule != null && rule.enabled(sourceElement)) {
						if (late==rule.fireLate() && rule.dependenciesOK(sourceElement, generatedElements)){
							generatedElements.addAll(rule.fire(sourceElement, generatedElements));
							firedRules.add(rule);
						}
					}
				}
				if (firedRules.size()>0) {
					progress = true;
					deferredRules.get(sourceElement).removeAll(firedRules);
				}
				if (deferredRules.get(sourceElement).size()==0){
					empties.add(sourceElement);
				}
			}
			
			deferredRules.keySet().removeAll(empties);
			if (progress == false) {
				if (late){ //o-oh, no progress when already doing the late rules 
					throw new Exception(Messages.GENERATOR_MSG_00);
				}
				late = true; //enable the late rules
			}
		} 
	}
	
	/*
	 * recursive routine to traverse model firing appropriate rules
	 */
	private void traverseModel(final EObject sourceElement2) throws Exception {
		
		//this ensures that we do not generate from our own generated elements
		if (importerConfig.generatorID.equals(importerConfig.adapter.getGeneratorId(sourceElement2)))
				return;
		
		//try to fire all the rules listed for this kind of source element
		List<IRule> rules = new ArrayList<IRule>();
		List<EClass> types = new ArrayList<EClass>();
		types.addAll(sourceElement2.eClass().getEAllSuperTypes());
		types.add(sourceElement2.eClass());
		for (EClass type : types ){
			if (importerConfig.ruleMapping.get(type)!=null)
				rules.addAll(importerConfig.ruleMapping.get(type));
		}
		for (final IRule rule : rules){
			if (rule != null && rule.enabled(sourceElement2)) {
				if (!rule.fireLate() && rule.dependenciesOK(sourceElement2, generatedElements)){
					generatedElements.addAll(rule.fire(sourceElement2, generatedElements));
				}else{
					defer(sourceElement2,rule);
				}
			}
		}

		for (final EObject child : sourceElement2.eContents()) {
				traverseModel(child);
		}
	}
	

}