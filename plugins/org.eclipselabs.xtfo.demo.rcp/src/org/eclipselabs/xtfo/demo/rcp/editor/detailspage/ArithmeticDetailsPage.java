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

import org.eclipse.emf.common.command.CompoundCommand;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.edit.command.AddCommand;
import org.eclipse.emf.edit.command.DeleteCommand;
import org.eclipse.emf.edit.command.SetCommand;
import org.eclipse.emf.edit.domain.EditingDomain;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.VerifyKeyListener;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.xtext.example.arithmetics.arithmetics.Evaluation;
import org.eclipse.xtext.example.arithmetics.ui.internal.ArithmeticsActivator;
import org.eclipse.xtext.parser.IParseResult;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.ui.editor.model.IXtextDocument;
import org.eclipse.xtext.ui.editor.model.IXtextModelListener;
import org.eclipse.xtext.util.concurrent.IUnitOfWork;
import org.eclipselabs.xtfo.demo.metamodel.demo.ArithmeticWrapper;
import org.eclipselabs.xtfo.demo.metamodel.demo.DemoPackage;
import org.eclipselabs.xtfo.demo.rcp.editor.ActionBarContributor;
import org.eclipselabs.xtfo.demo.rcp.editor.RCPEditor;
import org.eclipselabs.xtfo.demo.rcp.partialEditing.EmbeddedXtextEditor;

import com.google.inject.Injector;


public class ArithmeticDetailsPage extends EObjectAbstractDetailsPage {

	private IManagedForm managedForm;
	private FormToolkit toolkit;
	private EmbeddedXtextEditor editor;
	
	public ArithmeticDetailsPage(RCPEditor rcpEditor) {
		super(rcpEditor);
	}
	
	@Override
	protected ArithmeticWrapper getEditedEObject() {
		return (ArithmeticWrapper)super.getEditedEObject();
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

	public void createContents(Composite parent) {
		parent.setLayout(new GridLayout());
		parent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		Section s1 = toolkit.createSection(parent, Section.DESCRIPTION|Section.TITLE_BAR);
		s1.setLayout(new GridLayout());
		s1.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		s1.setText("Arithmetic Details"); //$NON-NLS-1$
		s1.setDescription("Arithmetic Details section desc"); //$NON-NLS-1$
		
		Composite client = toolkit.createComposite(s1);
		client.setLayout(new GridLayout());
		client.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		Injector injector = ArithmeticsActivator.getInstance().getInjector("org.eclipse.xtext.example.arithmetics.Arithmetics");
		Composite editorComposite = toolkit.createComposite(client);
		
		editorComposite.setLayout(new GridLayout());

		editor = new EmbeddedXtextEditor(editorComposite, injector, SWT.BORDER);

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
		
		editor.getViewer().getTextWidget().addVerifyKeyListener(new VerifyKeyListener() {
			public void verifyKey(VerifyEvent e) {
				if (e.keyCode == SWT.KEYPAD_CR || e.keyCode == SWT.CR) {
					e.doit = false;
				}
			}
		});
		
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, false);
		data.heightHint= convertHeightInCharsToPixels(1);
		editorComposite.setLayoutData(data);
		
		s1.setClient(client);
	}
	
	
	private int convertHeightInCharsToPixels(int i) {
		// Create a GC to calculate font's dimensions
	    GC gc = new GC(Display.getDefault());
	    gc.setFont(editor.getViewer().getTextWidget().getFont());

	    // Determine string's dimensions
	    FontMetrics fontMetrics = gc.getFontMetrics();

	    int ret = (fontMetrics.getHeight() + fontMetrics.getAscent() + fontMetrics.getDescent() + fontMetrics.getLeading()) * i;
	    
	    // Dispose that gc
	    gc.dispose();
		
	    return ret;
	}

	private boolean isDocumentHasErrors(final IXtextDocument xtextDocument) {
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
		editor.update(getEditedEObject().getEval(), asString == null ? "" : asString);
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
			compoundCommand.append(SetCommand.create(editingDomain, getEditedEObject(), DemoPackage.Literals.ARITHMETIC_WRAPPER__AS_STRING, editor.getViewer().getDocument().get()));
		}
		
		if (editor.getDocument() != null) { 
			if (isDocumentHasErrors(editor.getDocument())) {
				// Parsing error, we don't do aything
				updateEditedEObject = false;
			}
		} else {
			// document is null or already disposed, we don't do anything
			updateEditedEObject = false;
		}
		
		if (updateEditedEObject) {
			Evaluation astRootElement = getEditedEObject().getEval();
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
