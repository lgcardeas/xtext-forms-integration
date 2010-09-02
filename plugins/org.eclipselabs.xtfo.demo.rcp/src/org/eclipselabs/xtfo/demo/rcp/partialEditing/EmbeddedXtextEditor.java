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
 *    itemis AG - source viewer configuration
 *    Sebastian Zarnekow (itemis AG) - synthetic resource creation and source viewer configuration 
 */
package org.eclipselabs.xtfo.demo.rcp.partialEditing;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.expressions.Expression;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.compare.diff.metamodel.DiffModel;
import org.eclipse.emf.compare.diff.service.DiffService;
import org.eclipse.emf.compare.match.MatchOptions;
import org.eclipse.emf.compare.match.metamodel.MatchModel;
import org.eclipse.emf.compare.match.service.MatchService;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.commands.ActionHandler;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ISynchronizable;
import org.eclipse.jface.text.source.AnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.ActiveShellExpression;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.handlers.IHandlerActivation;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.texteditor.AnnotationPreference;
import org.eclipse.ui.texteditor.DefaultMarkerAnnotationAccess;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;
import org.eclipse.ui.texteditor.MarkerAnnotationPreferences;
import org.eclipse.ui.texteditor.SourceViewerDecorationSupport;
import org.eclipse.xtext.Constants;
import org.eclipse.xtext.IGrammarAccess;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.ui.editor.XtextSourceViewer;
import org.eclipse.xtext.ui.editor.XtextSourceViewerConfiguration;
import org.eclipse.xtext.ui.editor.model.IXtextDocument;
import org.eclipse.xtext.ui.editor.model.XtextDocument;
import org.eclipse.xtext.ui.editor.preferences.IPreferenceStoreAccess;
import org.eclipse.xtext.ui.editor.quickfix.IssueResolutionProvider;
import org.eclipse.xtext.ui.editor.validation.AnnotationIssueProcessor;
import org.eclipse.xtext.ui.editor.validation.IValidationIssueProcessor;
import org.eclipse.xtext.ui.editor.validation.ValidationJob;
import org.eclipse.xtext.ui.resource.IResourceSetProvider;
import org.eclipse.xtext.util.StringInputStream;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.name.Named;

public class EmbeddedXtextEditor {

	public static final String SYNTHETIC_SCHEME = "synthetic";
	
	private Composite fControl;
	private int fStyle;
	
	private XtextSourceViewer fSourceViewer;
	private XtextResource fResource;
	private XtextDocument fDocument;
	
	@Inject
	@Named(Constants.FILE_EXTENSIONS)
	private String fFileExtension;

	private XtextSourceViewerConfiguration fViewerConfiguration;
	
	@Inject
	private HighlightingHelper fHighlightingHelper;
	
	@Inject
	private IResourceSetProvider fResourceSetProvider;

	@Inject
	private IGrammarAccess fGrammarAccess;
	
	@Inject
	private XtextSourceViewer.Factory fSourceViewerFactory;

	@Inject
	private Provider<XtextSourceViewerConfiguration> fSourceViewerConfigurationProvider;

	@Inject
	private Provider<XtextDocument> fDocumentProvider;

	@Inject
	private IResourceValidator fResourceValidator;

	@Inject
	private IPreferenceStoreAccess fPreferenceStoreAccess;

//	private ITextListener fTextListener;
	private boolean fDeselectOnNextUpdate;
	
	private ActionHandler contentAssistAction = new ActionHandler(new Action() {
		public void run() {
			fSourceViewer.getTextOperationTarget().doOperation(ISourceViewer.CONTENTASSIST_PROPOSALS);
		};
	});

	/**
	 * Creates a new EmbeddedXtextEditor.
	 * 
	 * @param control the parent composite that will contain the editor
	 * @param injector the Guice injector to get Xtext configuration elements
	 * @param job the synchronization job that will be scheduled/rescheduled at each 
	 * 		modification of the editor text. It may be use to reconcile the content of 
	 * 		the editor with something else. 
	 * @param style the SWT style of the {@link SourceViewer} of this editor.
	 * @param fileExtension the file extension (without the DOT) of the textual DSL to edit
	 */
	public EmbeddedXtextEditor(Composite control, Injector injector, int style) {
		fControl = control;
		fStyle = style;

		injector.injectMembers(this);
		
		createEditor(fControl);
	}
	
	/**
	 * Creates a new EmbeddedXtextEditor.
	 * 
	 * Equivalent to EmbeddedXtextEditor(control, injector, job, fileExtension, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
	 * 
	 * @param control the parent composite that will contain the editor
	 * @param injector the Guice injector to get Xtext configuration elements
	 * @param job the synchronization job that will be scheduled/rescheduled at each 
	 * 		modification of the editor text. It may be use to reconcile the content of 
	 * 		the editor with something else. 
	 * @param fileExtension the file extension (without the DOT) of the textual DSL to edit
	 * @param fileExtension
	 */
	public EmbeddedXtextEditor(Composite control, Injector injector) {
		this(control, injector, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
	}

	public Composite getControl() {
		return fControl;
	}
	
	public XtextSourceViewer getViewer() {
		return fSourceViewer;
	}
	
	public XtextResource getResource() {
		return fResource;
	}
	
	public IXtextDocument getDocument() {
		return fDocument;
	}
	
	/**
	 * Should be called only once, during initialization. 
	 * 
	 * Then, you should call {@link #updateText(String, String, String)};
	 * 
	 * @param document
	 * @param prefix
	 * @param editablePart
	 * @param suffix
	 */
	protected void setText(XtextDocument document, String editablePart) {
		document.set(editablePart);
		fResource = createResource(editablePart);
		document.setInput(fResource);
		AnnotationModel annotationModel = new AnnotationModel();
		if (document instanceof ISynchronizable) {
			Object lock= ((ISynchronizable)document).getLockObject();
			if (lock == null) {
				lock= new Object();
				((ISynchronizable)document).setLockObject(lock);
			}
			((ISynchronizable) annotationModel).setLockObject(lock);
		}
		fSourceViewer.setDocument(document, annotationModel);
		fSourceViewer.setSelectedRange(0, -1);
	}
	
	private XtextResource createResource(String content) {
		XtextResource result = createResource();
		try {
			result.load(new StringInputStream(content, result.getEncoding()), Collections.emptyMap());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return result;
	}
	
	private void createEditor(Composite parent) {
		createViewer(parent);
		Control control = fSourceViewer.getControl();
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		control.setLayoutData(data);

		fSourceViewer.getTextWidget().addFocusListener(new FocusListener() {
			private final IHandlerService handlerService= (IHandlerService) PlatformUI.getWorkbench().getAdapter(IHandlerService.class);
			private final Expression expression = new ActiveShellExpression(fSourceViewer.getControl().getShell());
			private IHandlerActivation handlerActivation;

			public void focusLost(FocusEvent e) {
				handlerService.deactivateHandler(handlerActivation);
			}
			public void focusGained(FocusEvent e) {
				handlerActivation = handlerService.activateHandler(
						ITextEditorActionDefinitionIds.CONTENT_ASSIST_PROPOSALS, 
						contentAssistAction, 
						expression);
			}
		});
	}
	
	private void createViewer(Composite parent) {
		createSourceViewerHandle(parent);
		setText(fDocument, "");
		fHighlightingHelper.install(fViewerConfiguration, fSourceViewer);
	}
	
	private void createSourceViewerHandle(Composite parent) {
		fSourceViewer = fSourceViewerFactory.createSourceViewer(parent, null, null, false, fStyle);
		fViewerConfiguration = fSourceViewerConfigurationProvider.get();
		fSourceViewer.configure(fViewerConfiguration);
		
		final SourceViewerDecorationSupport viewerDecorationSupport = new SourceViewerDecorationSupport(fSourceViewer, null, new DefaultMarkerAnnotationAccess(), EditorsUI.getSharedTextColors());
		MarkerAnnotationPreferences annotationPreferences = new MarkerAnnotationPreferences();
		Iterator<AnnotationPreference> e= Iterators.filter(annotationPreferences.getAnnotationPreferences().iterator(), AnnotationPreference.class);
		while (e.hasNext())
			viewerDecorationSupport.setAnnotationPreference(e.next());
		viewerDecorationSupport.install(fPreferenceStoreAccess.getPreferenceStore());
		parent.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				viewerDecorationSupport.dispose();
			}
		});
		fDocument = fDocumentProvider.get();
		ValidationJob job = new ValidationJob(fResourceValidator, fDocument, 
				new IValidationIssueProcessor() {
					private AnnotationIssueProcessor annotationIssueProcessor;
					
					public void processIssues(List<Issue> issues, IProgressMonitor monitor) {
						if (annotationIssueProcessor == null) {
							annotationIssueProcessor = new AnnotationIssueProcessor(fDocument, 
									fSourceViewer.getAnnotationModel(), 
									new IssueResolutionProvider.NullImpl());
						}
						if (annotationIssueProcessor != null)
							annotationIssueProcessor.processIssues(issues, monitor);						
					}
				}, CheckMode.FAST_ONLY);
		fDocument.setValidationJob(job);
	}
	
	/**
	 * Updates the text of this editor with the given String
	 * 
	 * @param model
	 */
	public void update(String model) {
		IDocument document = fSourceViewer.getDocument();
		
		fSourceViewer.setRedraw(false);
		document.set(model);
		fSourceViewer.setVisibleRegion(0, model.length());
		fSourceViewer.setRedraw(true);
		
		// Fix strange behavior when update(EObject) catch XtextSerializationException
		if (fDeselectOnNextUpdate) {
			fDeselectOnNextUpdate = false;
			fSourceViewer.setSelectedRange(0, 0);
		}
		
		this.fDeselectOnNextUpdate = "".equals(model);
	}
	
	/**
	 * Updates the text of this editor with the given String or the 
	 * serialized form of the EObject if the semantic model of the 
	 * String does not contain any error and is different from the 
	 * given EObject.
	 * 
	 * @param eObject
	 * @param asString
	 */
	public void update(EObject eObject, String asString) {
		if (eObject != null) {
			EObject asStringEObject = null;
			XtextResource asStringResource = (XtextResource) fResourceSetProvider.get(null).createResource(URI.createURI(SYNTHETIC_SCHEME + ":/asStringResource." + fFileExtension));
			try {
				asStringResource.load(new StringInputStream(asString), Collections.emptyMap());
				if (!asStringResource.getContents().isEmpty()) {
					asStringEObject = asStringResource.getContents().get(0);
				}
			} catch (IOException e) {
				// ignore, will set the string to the serialization of the given eObject
			}
			
			if (!asStringResource.getErrors().isEmpty() || (asStringResource.getParseResult() != null && !asStringResource.getParseResult().getParseErrors().isEmpty())) {
				// if there are parsing errors in the saved string, then we update with it
				update(asString);
			} else if (asStringEObject != null) {
				try {
					Resource copyResource = (XtextResource) fResourceSetProvider.get(null).createResource(URI.createURI(SYNTHETIC_SCHEME + ":/copyResource." + fFileExtension));
					EObject copyEObject = EcoreUtil.copy(eObject);
					copyResource.getContents().add(copyEObject);
					if (!equals(copyEObject, asStringEObject)) {
//						String model = getResource().getSerializer().serialize(copyEObject, SaveOptions.newBuilder().noValidation().format().getOptions());
						update(asString); // FIXME: should update with the serialized form of the copyEObject but throw RuntimeException!!! 
					} else {
						// if there is no error and the content are equals, then we also update with the string
						update(asString);
					}
					copyResource.unload();
					copyResource.getResourceSet().getResources().remove(copyResource);
				} catch (Exception e) {
					update(asString);
				}
			} else {
				update("");
			}
			
			asStringResource.unload();
			asStringResource.getResourceSet().getResources().remove(asStringResource);
		} else {
			update("");
		}
	}
	
	protected XtextResource createResource() {
		ResourceSet resourceSet = fResourceSetProvider.get(null);
		Resource grammarResource = resourceSet.createResource(
				URI.createURI(SYNTHETIC_SCHEME + ":/" + fGrammarAccess.getGrammar().getName() + ".xtext"));
		grammarResource.getContents().add(EcoreUtil.copy(fGrammarAccess.getGrammar()));
		XtextResource result = (XtextResource) resourceSet.createResource(
				URI.createURI(SYNTHETIC_SCHEME + ":/" + fGrammarAccess.getGrammar().getName() + "." + fFileExtension));
		resourceSet.getResources().add(result);
		return result;
	}
		
	private static boolean equals(EObject expected, EObject actual) {
		Map<String, Object> options = ImmutableMap.<String, Object> builder().put(MatchOptions.OPTION_IGNORE_XMI_ID, Boolean.TRUE).build();
	    MatchModel match = null;
	    try
	    {
	        match = MatchService.doMatch(expected, actual, options);
	        DiffModel diff = DiffService.doDiff(match, false);
	        return diff.getDifferences().isEmpty();
	    }
	    catch (InterruptedException e)
	    {
	        throw new AssertionError(e);
	    }
	}
	
}
