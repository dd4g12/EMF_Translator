package ac.soton.eventb.emf.diagrams.importExport.command;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.workspace.AbstractEMFOperation;
import org.rodinp.core.RodinCore;
import org.rodinp.core.RodinDBException;

import ac.soton.eventb.emf.diagrams.importExport.Activator;
import ac.soton.eventb.emf.diagrams.importExport.impl.Importer;
import ac.soton.eventb.emf.diagrams.importExport.impl.ImporterFactory;
import ac.soton.eventb.emf.diagrams.importExport.impl.Messages;

//////////////////////GENERATE COMMAND//////////////////////////
public class ImportCommand extends AbstractEMFOperation {

	private EObject element;

	/**
	 * @param domain
	 * @param label
	 * @param affectedFiles
	 */
	public ImportCommand(TransactionalEditingDomain editingDomain, EObject rootEl) {
		super(editingDomain, Messages.GENERATOR_MSG_11, null);
		setOptions(Collections.singletonMap(Transaction.OPTION_UNPROTECTED, Boolean.TRUE));
		if (rootEl.eIsProxy()){
			this.element = EcoreUtil.resolve(rootEl, editingDomain.getResourceSet());
		}else{
			this.element = rootEl;			
		}
	}
	
	@Override
	public boolean canExecute(){
		return super.canExecute() && element!=null && !element.eIsProxy();
	}
	
	@Override
	public boolean canRedo(){
		return false;
	}

	@Override
	public boolean canUndo(){
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.gmf.runtime.emf.commands.core.command.AbstractTransactionalCommand#doExecuteWithResult(org.eclipse.core.runtime.IProgressMonitor, org.eclipse.core.runtime.IAdaptable)
	 */
	@Override
	protected IStatus doExecute(IProgressMonitor monitor, IAdaptable info) {
		
		try {
			
			RodinCore.run(new IWorkspaceRunnable() {
				public void run(final IProgressMonitor monitor) throws CoreException{
					TransactionalEditingDomain editingDomain = getEditingDomain();
					final List<Resource> modifiedResources;
					
					monitor.beginTask(Messages.GENERATOR_MSG_12,10);		
					monitor.setTaskName(Messages.GENERATOR_MSG_13(element)); 

					// flush the command stack as this is unprotected and has no undo/redo
					//editingDomain.getCommandStack().flush();		//Was causing an exception in Rodin editor - seems ok without this!
					
					monitor.worked(1);
			        monitor.subTask(Messages.GENERATOR_MSG_14);
					//try to create an appropriate generator
			        
					Importer importer = ImporterFactory.getFactory().createGenerator(element.eClass());
					monitor.worked(2);
			        monitor.subTask(Messages.GENERATOR_MSG_15);
			        
			        //try to run the generation
					modifiedResources = importer.generate(editingDomain,element);
					
					
					monitor.worked(4);
					if (modifiedResources == null){
						
						//ErrorDialog errorDialog = new ErrorDialog(diagramEditor.getSite().getShell(), label, label, null, 0); 
						//should display a message here 
						//"Generation Failed - see error log for details"
						
					}else{
//						if (element.eIsProxy()){
//							element = EcoreUtil.resolve(element, editingDomain.getResourceSet());
//						}
//						modifiedResources.add(element.eResource());
						//try to save all the modified resources
				        monitor.subTask(Messages.GENERATOR_MSG_16);
				        List<Resource> saved = new ArrayList<Resource>();
						for (Resource resource : modifiedResources){
							try {
								if (!saved.contains(resource)){
									resource.save(Collections.emptyMap());
									saved.add(resource);
								}
								monitor.worked(1);
							} catch (IOException e) {
								//throw this as a CoreException
								throw new CoreException(
										new Status(Status.ERROR, Activator.PLUGIN_ID, Messages.GENERATOR_MSG_18(resource), e));
							}					
						}
						

					}
				monitor.done();
				}
			},monitor);
			
			return Status.OK_STATUS;

		} catch (RodinDBException e) {
			Activator.logError(Messages.GENERATOR_MSG_19, e);
			return e.getStatus();
			
		} finally {
			monitor.done();
		}
	}

}