/**
 * Copyright (c) 2010 ProxiAD
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Obeo - initial API and implementation
 */
package org.eclipselabs.xtfo.demo.rcp.editor.detailspage;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.command.CompoundCommand;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.edit.command.AddCommand;
import org.eclipse.emf.edit.command.DeleteCommand;
import org.eclipse.emf.edit.command.SetCommand;
import org.eclipse.emf.edit.domain.EditingDomain;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.xtext.example.ui.internal.DomainmodelActivator;
import org.eclipse.xtext.parser.IParseResult;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.ui.editor.model.IXtextDocument;
import org.eclipse.xtext.ui.editor.model.IXtextModelListener;
import org.eclipse.xtext.util.concurrent.IUnitOfWork;
import org.eclipselabs.xtfo.demo.metamodel.demo.DemoPackage;
import org.eclipselabs.xtfo.demo.metamodel.demo.DomainModelWrapper;
import org.eclipselabs.xtfo.demo.rcp.editor.ActionBarContributor;
import org.eclipselabs.xtfo.demo.rcp.editor.RCPEditor;
import org.eclipselabs.xtfo.demo.rcp.partialEditing.EmbeddedXtextEditor;

import com.google.inject.Injector;


public class DomainModelDetailsPage extends EObjectAbstractDetailsPage {

	private IManagedForm managedForm;
	private FormToolkit toolkit;
	private EmbeddedXtextEditor editor;
	
	public DomainModelDetailsPage(RCPEditor rcpEditor) {
		super(rcpEditor);
	}
	
	@Override
	protected DomainModelWrapper getEditedEObject() {
		return (DomainModelWrapper)super.getEditedEObject();
	}
	
	public void initialize(IManagedForm form) {
		this.managedForm = form;
		toolkit = managedForm.getToolkit();
	}

	public void dispose() {
		toolkit.dispose();
	}

	public boolean isDirty() {
		return false;
	}

	public void commit(boolean onSave) {

	}

	public boolean setFormInput(Object input) {
		return true;
	}

	public void setFocus() {

	}

	public boolean isStale() {
		return false;
	}

	public void refresh() {

	}

	Text textWidget;
	ModifyListener modifyListener;
	Job update = new Job("update") {
		@Override
		protected IStatus run(IProgressMonitor monitor) {
			GetValue r = new GetValue();
			Display.getDefault().syncExec(r);
			Command setCommand = SetCommand.create(getEditor().getEditingDomain(), getEditedEObject(), DemoPackage.Literals.DOMAIN_MODEL_WRAPPER__NAME, r.get());
			getEditor().getEditingDomain().getCommandStack().execute(setCommand);
			return Status.OK_STATUS;
		}
	};
	
	private final class GetValue implements Runnable {
		String value = "";
	
		public void run() {
			value = textWidget.getText();
		}
		
		public String get() {
			return value;
		}
	}
	
	public void createContents(Composite parent) {
		parent.setLayout(new GridLayout());
		parent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		Section s1 = toolkit.createSection(parent, Section.DESCRIPTION|Section.TITLE_BAR);
		s1.setLayout(new GridLayout());
		s1.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		s1.setText("Domain Model Details"); //$NON-NLS-1$
		s1.setDescription("Domain Model section desc"); //$NON-NLS-1$
		
		Composite client = toolkit.createComposite(s1);
		client.setLayout(new GridLayout(2, false));
		client.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		toolkit.createLabel(client, "Name: ");
		textWidget = toolkit.createText(client, "", SWT.SINGLE|SWT.BORDER);
		textWidget.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		
		modifyListener = new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				if (update.cancel()) {
					update.schedule(500L); 
				} else {
					try {
						update.join();
						update.schedule();
					} catch (InterruptedException exception) {
						exception.printStackTrace();
					}
				}
			}
		};
		textWidget.addModifyListener(modifyListener);
		
		Injector injector = DomainmodelActivator.getInstance().getInjector("org.eclipse.xtext.example.Domainmodel");
		Composite editorComposite = toolkit.createComposite(client);
		
		editorComposite.setLayout(new GridLayout());
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		data.horizontalSpan = 2;
		editorComposite.setLayoutData(data);
		
		editor = new EmbeddedXtextEditor(editorComposite, injector);
		
		editor.getDocument().addModelListener(new IXtextModelListener() {
			public void modelChanged(XtextResource resource) {
				reconcileChangedModel();
			}
		});

		// we must deactivate the Cut/Copy/Paste global actions contributed by the EditingDomain action bar
		editor.getViewer().getTextWidget().addFocusListener(new FocusListener() {
			public void focusLost(FocusEvent e) {
				((ActionBarContributor)getEditor().getActionBarContributor()).activateCCPActions();
			}
			
			public void focusGained(FocusEvent e) {
				((ActionBarContributor)getEditor().getActionBarContributor()).deactivateCCPActions();
			}
		});
		
		s1.setClient(client);
	}

	private boolean documentHasErrors(final IXtextDocument xtextDocument) {
		return (xtextDocument.readOnly(new IUnitOfWork<Boolean, XtextResource>() {
			public Boolean exec(XtextResource state) throws Exception {
				IParseResult parseResult = state.getParseResult();
				return !state.getErrors().isEmpty() || parseResult == null || !parseResult.getParseErrors().isEmpty();
			}
		}));
	}
	
	@Override
	protected void update() {
		String asString = getEditedEObject().getAsString();
		editor.update(getEditedEObject().getAstRootElement(), asString == null ? "" : asString);

		textWidget.removeModifyListener(modifyListener);
		textWidget.setText(getEditedEObject().getName() == null ? "" : getEditedEObject().getName());
		textWidget.addModifyListener(modifyListener);
	}

	private void reconcileChangedModel() {
		// The command to execute
		CompoundCommand compoundCommand = new CompoundCommand() {
			@Override
			public Collection<?> getAffectedObjects() {
				return Collections.singletonList(getEditedEObject());
			}
		};
		
		EditingDomain editingDomain = getEditor().getEditingDomain();
		
		// we will update the edited EObject only if there is a diff in the source viewer
		boolean updateEditedEObject = !editor.getViewer().getDocument().get().equals(getEditedEObject().getAsString());
		
		if (updateEditedEObject) {
			compoundCommand.append(SetCommand.create(editingDomain, getEditedEObject(), DemoPackage.Literals.DOMAIN_MODEL_WRAPPER__AS_STRING, editor.getViewer().getDocument().get()));
		}
		
		if (editor.getDocument() != null) { 
			if (documentHasErrors(editor.getDocument())) {
				// Parsing error, we don't do aything
				updateEditedEObject = false;
			}
		} else {
			// document is null or already disposed, we don't do anything
			updateEditedEObject = false;
		}
		
		if (updateEditedEObject) {
			org.eclipse.xtext.example.domainmodel.DomainModel astRootElement = getEditedEObject().getAstRootElement();
			if (!astRootElement.eContents().isEmpty()) {
				// remove all previous elements in the edited object
				compoundCommand.append(DeleteCommand.create(editingDomain, astRootElement.eContents()));
			}
			
			EObject rootASTElement = editor.getResource().getParseResult().getRootASTElement();			
			if (rootASTElement != null && !rootASTElement.eContents().isEmpty()) {
				Iterator<EObject> rootASTContentIt = rootASTElement.eContents().iterator();
				while (rootASTContentIt.hasNext()) {
					final EObject original = rootASTContentIt.next();
					EObject copy = EcoreUtil.copy(original);
					EStructuralFeature eContainingFeature = original.eContainingFeature();
					if (eContainingFeature.isMany()) {
						compoundCommand.append(AddCommand.create(editingDomain, astRootElement, eContainingFeature, Collections.singletonList(copy)));
					} else {
						compoundCommand.append(SetCommand.create(editingDomain, astRootElement, eContainingFeature, copy));
					}
				}
			}
		}
		
		editingDomain.getCommandStack().execute(compoundCommand);
	}

}
