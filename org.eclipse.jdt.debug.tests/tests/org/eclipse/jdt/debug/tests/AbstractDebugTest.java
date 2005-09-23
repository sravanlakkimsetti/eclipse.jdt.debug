/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.debug.tests;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IBreakpointManager;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.ILineBreakpoint;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugModelPresentation;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.debug.core.IJavaClassPrepareBreakpoint;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaExceptionBreakpoint;
import org.eclipse.jdt.debug.core.IJavaLineBreakpoint;
import org.eclipse.jdt.debug.core.IJavaMethodBreakpoint;
import org.eclipse.jdt.debug.core.IJavaPatternBreakpoint;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaStratumLineBreakpoint;
import org.eclipse.jdt.debug.core.IJavaTargetPatternBreakpoint;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.jdt.debug.core.IJavaWatchpoint;
import org.eclipse.jdt.debug.core.JDIDebugModel;
import org.eclipse.jdt.debug.eval.EvaluationManager;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jdt.debug.eval.IEvaluationListener;
import org.eclipse.jdt.debug.eval.IEvaluationResult;
import org.eclipse.jdt.debug.testplugin.DebugElementEventWaiter;
import org.eclipse.jdt.debug.testplugin.DebugElementKindEventDetailWaiter;
import org.eclipse.jdt.debug.testplugin.DebugElementKindEventWaiter;
import org.eclipse.jdt.debug.testplugin.DebugEventWaiter;
import org.eclipse.jdt.internal.debug.ui.BreakpointUtils;
import org.eclipse.jdt.internal.debug.ui.IJDIPreferencesConstants;
import org.eclipse.jdt.internal.debug.ui.JDIDebugUIPlugin;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadPositionCategoryException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.util.SafeRunnable;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IHyperlink;
import org.eclipse.ui.console.TextConsole;
import org.eclipse.ui.internal.console.ConsoleHyperlinkPosition;

import sun.security.action.GetPropertyAction;


 
/**
 * Tests for launch configurations
 */
public abstract class AbstractDebugTest extends TestCase implements  IEvaluationListener {
	
	public static final int DEFAULT_TIMEOUT = 30000;
	
	public IEvaluationResult fEvaluationResult;
	
	public static IJavaProject fJavaProject;
	
	protected static MemberParser fMemberParser = null;
	
	/**
	 * The last relevent event set - for example, that caused
	 * a thread to suspend
	 */
	protected DebugEvent[] fEventSet;
	
	public AbstractDebugTest(String name) {
		super(name);
		// set error dialog to non-blocking to avoid hanging the UI during test
		ErrorDialog.AUTOMATED_MODE = true;
		SafeRunnable.setIgnoreErrors(true);
		if (!(this.getClass() == ProjectCreationDecorator.class) && !getJavaProject().exists()) {
			new TestSuite(ProjectCreationDecorator.class).run(new TestResult());
		}
	}
	
	/**
	 * Sets the last relevant event set
	 *
	 * @param set event set
	 */
	protected void setEventSet(DebugEvent[] set) {
		fEventSet = set;
	}
	
	/**
	 * Returns the last relevant event set
	 * 
	 * @return event set
	 */
	protected DebugEvent[] getEventSet() {
		return fEventSet;
	}
	
	/**
	 * Returns the launch manager
	 * 
	 * @return launch manager
	 */
	protected ILaunchManager getLaunchManager() {
		return DebugPlugin.getDefault().getLaunchManager();
	}
	
	/**
	 * Returns the breakpoint manager
	 * 
	 * @return breakpoint manager
	 */
	protected IBreakpointManager getBreakpointManager() {
		return DebugPlugin.getDefault().getBreakpointManager();
	}	
	
	/**
	 * Returns the 'DebugTests' project.
	 * 
	 * @return the test project
	 */
	protected IJavaProject getJavaProject() {
		return getJavaProject("DebugTests");
	}
	
	/**
	 * Returns the Java project with the given name.
	 * 
	 * @param name project name
	 * @return the Java project with the given name
	 */
	protected IJavaProject getJavaProject(String name) {
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
		return JavaCore.create(project);
	}
	
	/**
	 * Returns the source folder with the given name in the given project.
	 * 
	 * @param project
	 * @param name source folder name
	 * @return package fragment root
	 */
	protected IPackageFragmentRoot getPackageFragmentRoot(IJavaProject project, String name) {
		IProject p = project.getProject();
		return project.getPackageFragmentRoot(p.getFolder(name));
	}
	
	protected IHyperlink getHyperlink(int offset, IDocument doc) {
		if (offset >= 0 && doc != null) {
			Position[] positions = null;
			try {
				positions = doc.getPositions(ConsoleHyperlinkPosition.HYPER_LINK_CATEGORY);
			} catch (BadPositionCategoryException ex) {
				// no links have been added
				return null;
			}
			for (int i = 0; i < positions.length; i++) {
				Position position = positions[i];
				if (offset >= position.getOffset() && offset <= (position.getOffset() + position.getLength())) {
					return ((ConsoleHyperlinkPosition)position).getHyperLink();
				}
			}
		}
		return null;
	}
	
	/**
	 * Launches the given configuration and waits for an event. Returns the
	 * source of the event. If the event is not received, the launch is
	 * terminated and an exception is thrown.
	 * 
	 * @param configuration the configuration to launch
	 * @param waiter the event waiter to use
	 * @return Object the source of the event
	 * @exception Exception if the event is never received.
	 */
	protected Object launchAndWait(ILaunchConfiguration configuration, DebugEventWaiter waiter) throws CoreException {
	    return launchAndWait(configuration, waiter, true);
	}
	
	/**
	 * Launches the given configuration and waits for an event. Returns the
	 * source of the event. If the event is not received, the launch is
	 * terminated and an exception is thrown.
	 * 
	 * @param configuration the configuration to launch
	 * @param waiter the event waiter to use
	 * @param register whether to register the launch
	 * @return Object the source of the event
	 * @exception Exception if the event is never received.
	 */
	protected Object launchAndWait(ILaunchConfiguration configuration, DebugEventWaiter waiter, boolean register) throws CoreException {
		ILaunch launch = configuration.launch(ILaunchManager.DEBUG_MODE, null, false, register);
		Object suspendee= waiter.waitForEvent();
		if (suspendee == null) {
            System.out.println();
            System.out.println("Test case: " + this.getName());
            System.out.println("Never received event: " + waiter.getEventKindName());
            if (launch.isTerminated()) {
                System.out.println("Process exit value: " + launch.getProcesses()[0].getExitValue());
            }
            IConsole console = DebugUITools.getConsole(launch.getProcesses()[0]);
            if (console instanceof TextConsole) {
                TextConsole textConsole = (TextConsole)console;
                String string = textConsole.getDocument().get();
                System.out.println("Console output follows:");
                System.out.println(string);
            }
            System.out.println();
			try {
				launch.terminate();
			} catch (CoreException e) {
				e.printStackTrace();
				fail("Program did not suspend, and unable to terminate launch.");
			}
		}
		setEventSet(waiter.getEventSet());
		assertNotNull("Program did not suspend, launch terminated.", suspendee);
		return suspendee;		
	}	
	
	/**
	 * Launches the type with the given name, and waits for a
	 * suspend event in that program. Returns the thread in which the suspend
	 * event occurred.
	 * 
	 * @param mainTypeName the program to launch
	 * @return thread in which the first suspend event occurred
	 */
	protected IJavaThread launchAndSuspend(String mainTypeName) throws Exception {
		ILaunchConfiguration config = getLaunchConfiguration(mainTypeName);
		assertNotNull("Could not locate launch configuration for " + mainTypeName, config);
		return launchAndSuspend(config);
	}

	/**
	 * Launches the given configuration in debug mode, and waits for a 
	 * suspend event in that program. Returns the thread in which the suspend
	 * event occurred.
	 * 
	 * @param config the configuration to launch
	 * @return thread in which the first suspend event occurred
	 */	
	protected IJavaThread launchAndSuspend(ILaunchConfiguration config) throws Exception {
		DebugEventWaiter waiter= new DebugElementKindEventWaiter(DebugEvent.SUSPEND, IJavaThread.class);
		waiter.setTimeout(DEFAULT_TIMEOUT);
		Object suspendee = launchAndWait(config, waiter);
		return (IJavaThread)suspendee;		
	}
	
	/**
	 * Launches the type with the given name, and waits for a breakpoint-caused 
	 * suspend event in that program. Returns the thread in which the suspend
	 * event occurred.
	 * 
	 * @param mainTypeName the program to launch
	 * @return thread in which the first suspend event occurred
	 */
	protected IJavaThread launchToBreakpoint(String mainTypeName) throws Exception {
		return launchToBreakpoint(mainTypeName, true);
	}
	
	/**
	 * Launches the type with the given name, and waits for a breakpoint-caused 
	 * suspend event in that program. Returns the thread in which the suspend
	 * event occurred.
	 * 
	 * @param mainTypeName the program to launch
	 * @param register whether to register the launch
	 * @return thread in which the first suspend event occurred
	 */
	protected IJavaThread launchToBreakpoint(String mainTypeName, boolean register) throws Exception {
		ILaunchConfiguration config = getLaunchConfiguration(mainTypeName);
		assertNotNull("Could not locate launch configuration for " + mainTypeName, config);
		return launchToBreakpoint(config, register);
	}	

	/**
	 * Launches the given configuration in debug mode, and waits for a breakpoint-caused 
	 * suspend event in that program. Returns the thread in which the suspend
	 * event occurred.
	 * 
	 * @param config the configuration to launch
	 * @return thread in which the first suspend event occurred
	 */	
	protected IJavaThread launchToBreakpoint(ILaunchConfiguration config) throws CoreException {
	    return launchToBreakpoint(config, true);
	}
	
	/**
	 * Launches the given configuration in debug mode, and waits for a breakpoint-caused 
	 * suspend event in that program. Returns the thread in which the suspend
	 * event occurred.
	 * 
	 * @param config the configuration to launch
	 * @param whether to register the launch
	 * @return thread in which the first suspend event occurred
	 */	
	protected IJavaThread launchToBreakpoint(ILaunchConfiguration config, boolean register) throws CoreException {
		DebugEventWaiter waiter= new DebugElementKindEventDetailWaiter(DebugEvent.SUSPEND, IJavaThread.class, DebugEvent.BREAKPOINT);
		waiter.setTimeout(DEFAULT_TIMEOUT);

		Object suspendee= launchAndWait(config, waiter, register);
		assertTrue("suspendee was not an IJavaThread", suspendee instanceof IJavaThread);
		return (IJavaThread)suspendee;		
	}	
	
	/**
	 * Launches the type with the given name, and waits for a terminate
	 * event in that program. Returns the debug target in which the suspend
	 * event occurred.
	 * 
	 * @param mainTypeName the program to launch
	 * @param timeout the number of milliseconds to wait for a terminate event
	 * @return debug target in which the terminate event occurred
	 */
	protected IJavaDebugTarget launchAndTerminate(String mainTypeName) throws Exception {
		ILaunchConfiguration config = getLaunchConfiguration(mainTypeName);
		assertNotNull("Could not locate launch configuration for " + mainTypeName, config);
		return launchAndTerminate(config, DEFAULT_TIMEOUT);
	}

	/**
	 * Launches the given configuration in debug mode, and waits for a terminate
	 * event in that program. Returns the debug target in which the terminate
	 * event occurred.
	 * 
	 * @param config the configuration to launch
	 * @param timeout the number of milliseconds to wait for a terminate event
	 * @return thread in which the first suspend event occurred
	 */	
	protected IJavaDebugTarget launchAndTerminate(ILaunchConfiguration config, int timeout) throws Exception {
		DebugEventWaiter waiter= new DebugElementKindEventWaiter(DebugEvent.TERMINATE, IJavaDebugTarget.class);
		waiter.setTimeout(timeout);

		Object terminatee = launchAndWait(config, waiter);		
		assertNotNull("Program did not terminate.", terminatee);
		assertTrue("terminatee is not an IJavaDebugTarget", terminatee instanceof IJavaDebugTarget);
		IJavaDebugTarget debugTarget = (IJavaDebugTarget) terminatee;
		assertTrue("debug target is not terminated", debugTarget.isTerminated() || debugTarget.isDisconnected());
		return debugTarget;		
	}
	
	/**
	 * Launches the type with the given name, and waits for a line breakpoint suspend
	 * event in that program. Returns the thread in which the suspend
	 * event occurred.
	 * 
	 * @param mainTypeName the program to launch
	 * @param bp the breakpoint that should cause a suspend event
	 * @return thread in which the first suspend event occurred
	 */
	protected IJavaThread launchToLineBreakpoint(String mainTypeName, ILineBreakpoint bp) throws Exception {
		ILaunchConfiguration config = getLaunchConfiguration(mainTypeName);
		assertNotNull("Could not locate launch configuration for " + mainTypeName, config);
		return launchToLineBreakpoint(config, bp);
	}

	/**
	 * Launches the given configuration in debug mode, and waits for a line breakpoint 
	 * suspend event in that program. Returns the thread in which the suspend
	 * event occurred.
	 * 
	 * @param config the configuration to launch
	 * @param bp the breakpoint that should cause a suspend event
	 * @return thread in which the first suspend event occurred
	 */	
	protected IJavaThread launchToLineBreakpoint(ILaunchConfiguration config, ILineBreakpoint bp) throws Exception {
		DebugEventWaiter waiter= new DebugElementKindEventDetailWaiter(DebugEvent.SUSPEND, IJavaThread.class, DebugEvent.BREAKPOINT);
		waiter.setTimeout(DEFAULT_TIMEOUT);

		Object suspendee= launchAndWait(config, waiter);
		assertTrue("suspendee was not an IJavaThread", suspendee instanceof IJavaThread);
		IJavaThread thread = (IJavaThread) suspendee;
		IBreakpoint hit = getBreakpoint(thread);
		assertNotNull("suspended, but not by breakpoint", hit);
		assertTrue("hit un-registered breakpoint", bp.equals(hit));
		assertTrue("suspended, but not by line breakpoint", hit instanceof ILineBreakpoint);
		ILineBreakpoint breakpoint= (ILineBreakpoint) hit;
		int lineNumber = breakpoint.getLineNumber();
		int stackLine = thread.getTopStackFrame().getLineNumber();
		assertTrue("line numbers of breakpoint and stack frame do not match", lineNumber == stackLine);
		
		return thread;		
	}
	
	/**
	 * Resumes the given thread, and waits for another breakpoint-caused suspend event.
	 * Returns the thread in which the suspend event occurs.
	 * 
	 * @param thread thread to resume
	 * @return thread in which the first suspend event occurs
	 */
	protected IJavaThread resume(IJavaThread thread) throws Exception {
	    return resume(thread, DEFAULT_TIMEOUT);
	}	
	
	/**
	 * Resumes the given thread, and waits for another breakpoint-caused suspend event.
	 * Returns the thread in which the suspend event occurs.
	 * 
	 * @param thread thread to resume
	 * @param timeout timeout in ms
	 * @return thread in which the first suspend event occurs
	 */
	protected IJavaThread resume(IJavaThread thread, int timeout) throws Exception {
		DebugEventWaiter waiter= new DebugElementKindEventDetailWaiter(DebugEvent.SUSPEND, IJavaThread.class, DebugEvent.BREAKPOINT);
		waiter.setTimeout(timeout);
		
		thread.resume();

		Object suspendee= waiter.waitForEvent();
		setEventSet(waiter.getEventSet());
		assertNotNull("Program did not suspend.", suspendee);
		return (IJavaThread)suspendee;
	}	
	
	/**
	 * Resumes the given thread, and waits for a suspend event caused by the specified
	 * line breakpoint.  Returns the thread in which the suspend event occurs.
	 * 
	 * @param thread thread to resume
	 * @return thread in which the first suspend event occurs
	 */
	protected IJavaThread resumeToLineBreakpoint(IJavaThread resumeThread, ILineBreakpoint bp) throws Exception {
		DebugEventWaiter waiter= new DebugElementKindEventDetailWaiter(DebugEvent.SUSPEND, IJavaThread.class, DebugEvent.BREAKPOINT);
		waiter.setTimeout(DEFAULT_TIMEOUT);
		
		resumeThread.resume();

		Object suspendee= waiter.waitForEvent();
		setEventSet(waiter.getEventSet());
		assertNotNull("Program did not suspend.", suspendee);
		assertTrue("suspendee was not an IJavaThread", suspendee instanceof IJavaThread);
		IJavaThread thread = (IJavaThread) suspendee;
		IBreakpoint hit = getBreakpoint(thread);
		assertNotNull("suspended, but not by breakpoint", hit);
		assertTrue("hit un-registered breakpoint", bp.equals(hit));
		assertTrue("suspended, but not by line breakpoint", hit instanceof ILineBreakpoint);
		ILineBreakpoint breakpoint= (ILineBreakpoint) hit;
		int lineNumber = breakpoint.getLineNumber();
		int stackLine = thread.getTopStackFrame().getLineNumber();
		assertTrue("line numbers of breakpoint and stack frame do not match", lineNumber == stackLine);
		
		return (IJavaThread)suspendee;
	}	
	
	/**
	 * Resumes the given thread, and waits for the debug target
	 * to terminate (i.e. finish/exit the program).
	 * 
	 * @param thread thread to resume
	 */
	protected void exit(IJavaThread thread) throws Exception {
		DebugEventWaiter waiter= new DebugElementKindEventWaiter(DebugEvent.TERMINATE, IProcess.class);
		waiter.setTimeout(DEFAULT_TIMEOUT);
		
		thread.resume();

		Object suspendee= waiter.waitForEvent();
		setEventSet(waiter.getEventSet());
		assertNotNull("Program did not terminate.", suspendee);
	}	
		
	/**
	 * Resumes the given thread, and waits the associated debug
	 * target to terminate.
	 * 
	 * @param thread thread to resume
	 * @return the terminated debug target
	 */
	protected IJavaDebugTarget resumeAndExit(IJavaThread thread) throws Exception {
		DebugEventWaiter waiter= new DebugElementEventWaiter(DebugEvent.TERMINATE, thread.getDebugTarget());
		waiter.setTimeout(DEFAULT_TIMEOUT);
		
		thread.resume();

		Object suspendee= waiter.waitForEvent();
		setEventSet(waiter.getEventSet());
		assertNotNull("Program did not terminate.", suspendee);
		IJavaDebugTarget target = (IJavaDebugTarget)suspendee;
		assertTrue("program should have exited", target.isTerminated() || target.isDisconnected());
		return target;
	}	
		
	/**
	 * Returns the launch configuration for the given main type
	 * 
	 * @param mainTypeName program to launch
	 * @see ProjectCreationDecorator
	 */
	protected ILaunchConfiguration getLaunchConfiguration(String mainTypeName) {
		IFile file = getJavaProject().getProject().getFolder("launchConfigurations").getFile(mainTypeName + ".launch");
		ILaunchConfiguration config = getLaunchManager().getLaunchConfiguration(file);
		assertTrue("Could not find launch configuration for " + mainTypeName, config.exists());
		return config;
	}
	
	protected IResource getBreakpointResource(String typeName) throws Exception {
		IJavaElement element = getJavaProject().findElement(new Path(typeName + ".java"));
		IResource resource = element.getCorrespondingResource();
		if (resource == null) {
			resource = getJavaProject().getProject();
		}		
		return resource;
	}
	
	protected IResource getBreakpointResource(IType type) throws Exception {
		if (type == null) {
			return getJavaProject().getProject();
		}
		IResource resource = type.getCorrespondingResource();
		if (resource == null) {
			resource = getJavaProject().getProject();
		}		
		return resource;
	}	
	
	/**
	 * Creates and returns a line breakpoint at the given line number in the type with the
	 * given name.
	 * 
	 * @param lineNumber line number
	 * @param typeName type name
	 */
	protected IJavaLineBreakpoint createLineBreakpoint(int lineNumber, String typeName) throws Exception {
		IType type = getType(typeName);
		return createLineBreakpoint(type, lineNumber);
	}
	
	/**
	 * 
	 * @param lineNumber
	 * @param root
	 * @param packageName
	 * @param cuName
	 * @param fullTargetName
	 * @return
	 */
	protected IJavaLineBreakpoint createLineBreakpoint(int lineNumber, String root, String packageName, String cuName, 
			String fullTargetName) throws Exception{
		IJavaProject javaProject = getJavaProject();
		ICompilationUnit cunit = getCompilationUnit(javaProject, root, packageName, cuName);
		assertNotNull("did not find requested Compilation Unit", cunit);
		IType targetType = (IType)(new MemberParser()).getDeepest(cunit,fullTargetName);
		assertNotNull("did not find requested type", targetType);
		assertTrue("did not find type to install breakpoint in", targetType.exists());
		
		return createLineBreakpoint(targetType, lineNumber);
	}

	
	/**
	 * Creates a line breakpoint in the given type (may be a top level non public type)
	 * 
	 * @param lineNumber line number to create the breakpoint at
	 * @param packageName fully qualified package name containing the type, example "a.b.c"
	 * @param cuName simple name of compilation unit containing the type, example "Something.java"
	 * @param typeName $ qualified type name, example "Something" or "NonPublic" or "Something$Inner"
	 * @return line breakpoint
	 * @throws Exception
	 */
	protected IJavaLineBreakpoint createLineBreakpoint(int lineNumber, String packageName, String cuName, String typeName) throws Exception {
		IType type = getType(packageName, cuName, typeName);
		return createLineBreakpoint(type, lineNumber);
	}
	
	/**
	 * Creates a line breakpoint in the given type at the given line number.
	 * 
	 * @param type type in which to install the breakpoint
	 * @param lineNumber line number to install the breakpoint at
	 * @return line breakpoint
	 * @throws Exception
	 */
	protected IJavaLineBreakpoint createLineBreakpoint(IType type, int lineNumber) throws Exception {
		IMember member = null;
		if (type != null) {
			IJavaElement sourceElement = null;
			String source = null;
			if (type.isBinary()) {
				IClassFile classFile = type.getClassFile();
				source = classFile.getSource();
				sourceElement = classFile;
			} else {
				ICompilationUnit unit = type.getCompilationUnit();
				source = unit.getSource();
				sourceElement = unit;
			}
			// translate line number to offset
			if (source != null) {
				Document document = new Document(source);
				IRegion region = document.getLineInformation(lineNumber);
				if (sourceElement instanceof ICompilationUnit) {
					member = (IMember) ((ICompilationUnit)sourceElement).getElementAt(region.getOffset());
				} else {
					member = (IMember) ((IClassFile)sourceElement).getElementAt(region.getOffset());
				}
			}
		}
		Map map = getExtraBreakpointAttributes(member);
		return JDIDebugModel.createLineBreakpoint(getBreakpointResource(type), type.getFullyQualifiedName(), lineNumber, -1, -1, 0, true, map);		
	}
	
	/**
	 * Returns the type in the test project based on the given name. The type name may refer to a 
	 * top level non public type.
	 * 
	 * @param packageName package name, example "a.b.c"
	 * @param cuName simple compilation unit name within the package, example "Something.java"
	 * @param typeName simple dot qualified type name, example "Something" or "NonPublic" or "Something.Inner"
	 * @return associated type or <code>null</code> if none
	 * @throws Exception
	 */
	protected IType getType(String packageName, String cuName, String typeName) throws Exception {
		IPackageFragment[] packageFragments = getJavaProject().getPackageFragments();
		for (int i = 0; i < packageFragments.length; i++) {
			IPackageFragment fragment = packageFragments[i];
			if (fragment.getElementName().equals(packageName)) {
				ICompilationUnit compilationUnit = fragment.getCompilationUnit(cuName);
				String[] names = typeName.split("\\$");
				IType type = compilationUnit.getType(names[0]);
				for (int j = 1; j < names.length; j++) {
					type = type.getType(names[j]);
				}
				if (type.exists()) {
					return type;
				}
			}
		}
		return null;
	}
	
	/**
	 * Creates and returns a map of java element breakpoint attributes for a breakpoint on the
	 * given java element, or <code>null</code> if none
	 * 
	 * @param element java element the breakpoint is associated with
	 * @return map of breakpoint attributes or <code>null</code>
	 * @throws Exception
	 */
	protected Map getExtraBreakpointAttributes(IMember element) throws Exception {
		if (element != null && element.exists()) {
			Map map = new HashMap();
			ISourceRange sourceRange = element.getSourceRange();
			int start = sourceRange.getOffset();
			int end = start + sourceRange.getLength();
			IType type = null;
			if (element instanceof IType) {
				type = (IType) element;
			} else {
				type = element.getDeclaringType();
			}
			BreakpointUtils.addJavaBreakpointAttributesWithMemberDetails(map, type, start, end);
			return map;
		}
		return null;
	}	
	
	/**
	 * Creates and returns a line breakpoint at the given line number in the type with the
	 * given name and sets the specified condition on the breakpoint.
	 * 
	 * @param lineNumber line number
	 * @param typeName type name
	 * @param condition condition
	 */
	protected IJavaLineBreakpoint createConditionalLineBreakpoint(int lineNumber, String typeName, String condition, boolean suspendOnTrue) throws Exception {
		IJavaLineBreakpoint bp = createLineBreakpoint(lineNumber, typeName);
		bp.setCondition(condition);
		bp.setConditionEnabled(true);
		bp.setConditionSuspendOnTrue(suspendOnTrue);
		return bp;
	}
	
	/**
	 * Creates and returns a pattern breakpoint at the given line number in the
	 * source file with the given name.
	 * 
	 * @param lineNumber line number
	 * @param sourceName name of source file
	 * @param pattern the pattern of the class file name
	 */
	protected IJavaPatternBreakpoint createPatternBreakpoint(int lineNumber, String sourceName, String pattern) throws Exception {
		return JDIDebugModel.createPatternBreakpoint(getJavaProject().getProject(), sourceName, pattern, lineNumber, -1, -1, 0, true, null);
	}
	
	/**
	 * Creates and returns a target pattern breakpoint at the given line number in the
	 * source file with the given name.
	 * 
	 * @param lineNumber line number
	 * @param sourceName name of source file
	 */
	protected IJavaTargetPatternBreakpoint createTargetPatternBreakpoint(int lineNumber, String sourceName) throws Exception {
		return JDIDebugModel.createTargetPatternBreakpoint(getJavaProject().getProject(), sourceName, lineNumber, -1, -1, 0, true, null);
	}	
	
	/**
	 * Creates and returns a stratum breakpoint at the given line number in the
	 * source file with the given name.
	 * 
	 * @param lineNumber line number
	 * @param sourceName name of source file
	 * @param stratum the stratum of the source file
	 */
	protected IJavaStratumLineBreakpoint createStratumBreakpoint(int lineNumber, String sourceName, String stratum) throws Exception {
		return JDIDebugModel.createStratumBreakpoint(getJavaProject().getProject(), stratum, sourceName, null, null, lineNumber, -1, -1, 0, true, null);
	}
	
	/**
	 * Creates and returns a method breakpoint
	 * 
	 * @param typeNamePattern type name pattern
	 * @param methodName method name
	 * @param methodSignature method signature or <code>null</code>
	 * @param entry whether to break on entry
	 * @param exit whether to break on exit
	 */
	protected IJavaMethodBreakpoint createMethodBreakpoint(String typeNamePattern, String methodName, String methodSignature, boolean entry, boolean exit) throws Exception {
		IMethod method= null;
		if (methodSignature != null && methodName != null) {
			IType type = getType(typeNamePattern);
			if (type != null ) {
				method = type.getMethod(methodName, Signature.getParameterTypes(methodSignature));
			}
		}
		Map map = getExtraBreakpointAttributes(method);
		return JDIDebugModel.createMethodBreakpoint(getJavaProject().getProject(), typeNamePattern, methodName, methodSignature, entry, exit,false, -1, -1, -1, 0, true, map);
	}	
	
	/**
	 * Creates a method breakpoint in a fully specified type (potentially non public).
	 * 
	 * @param packageName package name containing type to install breakpoint in, example "a.b.c"
	 * @param cuName simple compilation unit name within package, example "Something.java"
	 * @param typeName $ qualified type name within compilation unit, example "Something" or
	 *  "NonPublic" or "Something$Inner"
	 * @param methodName method or <code>null</code> for all methods
	 * @param methodSignature JLS method siganture or <code>null</code> for all methods with the given name
	 * @param entry whether to break on entry
	 * @param exit whether to break on exit
	 * @return method breakpoint
	 * @throws Exception
	 */
	protected IJavaMethodBreakpoint createMethodBreakpoint(String packageName, String cuName, String typeName, String methodName, String methodSignature, boolean entry, boolean exit) throws Exception {
		IType type = getType(packageName, cuName, typeName);
		assertNotNull("did not find type to install breakpoint in", type);
		IMethod method= null;
		if (methodSignature != null && methodName != null) {
			if (type != null ) {
				method = type.getMethod(methodName, Signature.getParameterTypes(methodSignature));
			}
		}
		Map map = getExtraBreakpointAttributes(method);
		return JDIDebugModel.createMethodBreakpoint(getBreakpointResource(type), type.getFullyQualifiedName(), methodName, methodSignature, entry, exit,false, -1, -1, -1, 0, true, map);
	}
		

	/**
	 * Creates a MethodBreakPoint on the method specified at the given path. 
	 * Syntax:
	 * Type$InnerType$MethodNameAndSignature$AnonymousTypeDeclarationNumber$FieldName
	 * eg:<code>
	 * public class Foo{
	 * 		class Inner
	 * 		{
	 * 			public void aMethod()
	 * 			{
	 * 				Object anon = new Object(){
	 * 					int anIntField;
	 * 					String anonTypeMethod() {return "an Example";}				
	 * 				}
	 * 			}
	 * 		}
	 * }</code>
	 * Syntax to get the anonymous toString would be: Foo$Inner$aMethod()V$1$anonTypeMethod()QString
	 * so, createMethodBreakpoint(packageName, cuName, "Foo$Inner$aMethod()V$1$anonTypeMethod()QString",true,false);
	 */
	protected IJavaMethodBreakpoint createMethodBreakpoint(String root, String packageName, String cuName, 
									String fullTargetName, boolean entry, boolean exit) throws Exception {
		
		IJavaProject javaProject = getJavaProject();
		ICompilationUnit cunit = getCompilationUnit(javaProject, root, packageName, cuName);
		assertNotNull("did not find requested Compilation Unit", cunit);
		IMethod targetMethod = (IMethod)(new MemberParser()).getDeepest(cunit,fullTargetName);
		assertNotNull("did not find requested method", targetMethod);
		assertTrue("Given method does not exist", targetMethod.exists());
		IType methodParent = (IType)targetMethod.getParent();//safe - method's only parent = Type
		assertNotNull("did not find type to install breakpoint in", methodParent);
				
		Map map = getExtraBreakpointAttributes(targetMethod);
		return JDIDebugModel.createMethodBreakpoint(getBreakpointResource(methodParent), methodParent.getFullyQualifiedName(),targetMethod.getElementName(), targetMethod.getSignature(), entry, exit,false, -1, -1, -1, 0, true, map);
	}		
	
	/**
	 * Contains methods to find an IMember within a given path subdivided by the '$' character. 
	 * Syntax:
	 * Type$InnerType$MethodNameAndSignature$AnonymousTypeDeclarationNumber$FieldName
	 * eg:<code>
	 * public class Foo{
	 * 		class Inner
	 * 		{
	 * 			public void aMethod()
	 * 			{
	 * 				Object anon = new Object(){
	 * 					int anIntField;
	 * 					String anonTypeMethod() {return "an Example";}				
	 * 				}
	 * 			}
	 * 		}
	 * }</code>
	 * Syntax to get anIntField would be: Foo$Inner$aMethod()V$1$anIntField
	 * Syntax to get the anonymous toString would be: Foo$Inner$aMethod()V$1$anonTypeMethod()QString
	 * In the case of local types, the listed syntax should be Count and then Name, like: CountName
	 * eg:<code>1MyType</code>
	 */
	class MemberParser{
		/**
		 * @param cu the CompilationUnit containing the toplevel Type
		 * @param target - the IMember target, listed in full Syntax, as noted in MemberParser 
		 * eg: EnclosingType$InnerType
		 * @return the Lowest level inner type specified in input
		 */
		public IMember getDeepest(ICompilationUnit cu, String target)
		{
			for(int i=0;i<target.length();i++)
			{
				if(target.charAt(i)=='$')
				{//EnclosingType$InnerType$MoreInner
					String tail = target.substring(i+1);
					IType enclosure = cu.getType(target.substring(0, i));
					if(enclosure.exists())
						return getDeepest(enclosure,tail);
				}
			}
			//has no inner type
			return cu.getType(target);
			
		}
		
		/**
		 * Helper method for getLowestType (ICompilationUnit cu, String input)
		 * @param top name of enclosing Type
		 * @param tail the typename, possibly including inner type, 
		 * separated by $. 
		 * eg: EnclosingType$InnerType
		 * @return the designated type, or null if type not found.
		 */
		protected IMember getDeepest(IMember top, String tail) {
			
			if(tail==null || tail.length()==0 )
				return top;
			
			if(!top.exists())
				return null;
			
			//check if there are more nested elements
			String head=null;
			for(int i=0;i<tail.length();i++)
			{
				if(tail.charAt(i)=='$')//nested Item?
				{//Enclosing$Inner$MoreInner
					head = tail.substring(0,i);
					tail = tail.substring(i+1);	
					break;//found next item
				}
			}
			if(head==null)//we are at last item to parse
			{//swap Members
				head = tail;
				tail = null;
			}
			
			if(top instanceof IType)
				return getNextFromType(top, head, tail);
			else 
				if(top instanceof IMethod)
					return getNextFromMethod(top, head, tail);
				else
					if(top instanceof IField)
						return getNextFromField(top, head, tail);
			//else there is a problem!
			return getDeepest(top,tail);			
		}

		/**
		 * @param top the field in which to search
		 * @param head the next member to find
		 * @param tail the remaining members to find
		 * @return the next member down contained by the given Field
		 */
		protected IMember getNextFromField(IMember top, String head, String tail) {
			IField current = (IField)top;
			
			IType type = current.getType(getLocalTypeName(head),getLocalTypeOccurrence(head));
			if(type.exists())	
				return getDeepest(type,tail);
			//else
			return null;//something failed.								
		}

		/**
		 * @param top the member in which to search
		 * @param head the next member to find
		 * @param tail the remaining members to find
		 * @return the next member down contained by the given Method
		 */
		protected IMember getNextFromMethod(IMember top, String head, String tail) {
			//must be a local or anonymous type
			IMethod current = (IMethod)top;
										
			//is next part a Type?
			IType type = current.getType(getLocalTypeName(head), getLocalTypeOccurrence(head));
			if(type.exists())	
				return getDeepest(type,tail);
			//else
			return null;
		}

		/**
		 * @param top the member in which to search
		 * @param head the next member to find
		 * @param tail the remaining members to find
		 * @return the next member down contained by the given Type
		 */
		protected IMember getNextFromType(IMember top, String head, String tail) {
			IType current = (IType)top;
			
			//is next part a Type?
			IMember next = current.getType(head);
			if(next.exists())	
				return getDeepest(next,tail);
			//else, is next part a Field?
			next = current.getField(head);
			if(next.exists())
				return getDeepest(next,tail);
			//else, is next part a Method?
			next = current.getMethod(getName(head),getSignature(head));
			if(next.exists())
				return getDeepest(next,tail);
			//else
				return null;//something failed.
		}
		
		/**
		 * @param head the string to parse for a name
		 * @return the name in the type, given in the format "Occurance#Type"
		 * e.g. head = "1Type";
		 */
		protected String getLocalTypeName(String head) {
			for(int i=0;i<head.length();i++)
			{
				if(!Character.isDigit(head.charAt(i)))
				{
					return head.substring(i);
				}
				
			}
			return "";//entire thing is a number
		}

		/**
		 * @param head the string to parse for an occurance
		 * @return the name in the type, given in the format "Occurance#Type"
		 * e.g. head = "1Type";
		 */
		protected int getLocalTypeOccurrence(String head) {
			for(int i=0;i<head.length();i++)
			{
				if(!Character.isDigit(head.charAt(i)))
					return Integer.parseInt(head.substring(0, i));
			}
			return Integer.parseInt(head);//entire thing is a number
		}

		/**
		 * @param head name of method w/ signature at the end
		 * @return simply the ParameterTypeSignature, using format:
		 * methodNameSignature.
		 * e.g.  head = "someMethod()V"
		 */
		protected String[] getSignature(String head) {
			for(int i=0;i<head.length();i++)
			{
				if(head.charAt(i)=='(')//nested Item?
					return Signature.getParameterTypes(head.substring(i));
			}
				return null;
		}

		/**
		 * @param head name of method w/ signature at the end
		 * @return simply the name of the given method, using format:
		 * methodNameSignature.
		 * e.g.  head = "someMethod()V"
		 */
		protected String getName(String head) {
			for(int i=0;i<head.length();i++)
			{
				if(head.charAt(i)=='(')//nested Item?
					return head.substring(0,i);
			}
				return null;
		}
		
	}
	
	/**
	 * @param cu the Compilation where the target resides
	 * @param target the fullname of the target, as per MemberParser syntax
	 * @return the requested Member
	 */
	protected IMember getMember(ICompilationUnit cu, String target)
	{
		IMember toReturn = (new MemberParser()).getDeepest(cu,target);
		return toReturn;
	}
	/**
	 * Creates and returns a class prepare breakpoint on the type with the given fully qualified name.
	 * 
	 * @param typeName type on which to create the breakpoint
	 * @return breakpoint
	 * @throws Exception
	 */
	protected IJavaClassPrepareBreakpoint createClassPrepareBreakpoint(String typeName) throws Exception {
		return createClassPrepareBreakpoint(getType(typeName));
	}
	
	/**
	 * Creates and returns a class prepare breakpoint on the type with the given fully qualified name.
	 * 
	 * @param typeName type on which to create the breakpoint
	 * @return breakpoint
	 * @throws Exception
	 */
	protected IJavaClassPrepareBreakpoint createClassPrepareBreakpoint(String root,
			String packageName, String cuName, String fullTargetName) throws Exception {
		ICompilationUnit cunit = getCompilationUnit(getJavaProject(), root, packageName, cuName);
		IType type = (IType)getMember(cunit,fullTargetName);
		assertTrue("Target type not found", type.exists());
		return createClassPrepareBreakpoint(type);
	}	
	
	/**
	 * Creates a class prepare breakpoint in a fully specified type (potentially non public).
	 * 
	 * @param packageName package name containing type to install breakpoint in, example "a.b.c"
	 * @param cuName simple compilation unit name within package, example "Something.java"
	 * @param typeName $ qualified type name within compilation unit, example "Something" or
	 *  "NonPublic" or "Something$Inner"
	 */	
	protected IJavaClassPrepareBreakpoint createClassPrepareBreakpoint(String packageName, String cuName, String typeName) throws Exception {
		return createClassPrepareBreakpoint(getType(packageName, cuName, typeName));
	}
	
	/**
	 * Creates a class prepare breakpoint for the given type
	 * 
	 * @param type type
	 * @return class prepare breakpoint
	 * @throws Exception
	 */
	protected IJavaClassPrepareBreakpoint createClassPrepareBreakpoint(IType type) throws Exception {
		assertNotNull("type not specified for class prepare breakpoint", type);
		int kind = IJavaClassPrepareBreakpoint.TYPE_CLASS;
		if (type.isInterface()) {
			kind = IJavaClassPrepareBreakpoint.TYPE_INTERFACE;
		}
		Map map = getExtraBreakpointAttributes(type);
		return JDIDebugModel.createClassPrepareBreakpoint(getBreakpointResource(type), type.getFullyQualifiedName(), kind, -1, -1, true, map);		
	}
	
	/**
	 * Returns the Java model type from the test project with the given name or <code>null</code>
	 * if none.
	 * 
	 * @param typeName
	 * @return type or <code>null</code>
	 * @throws Exception
	 */
	protected IType getType(String typeName) throws Exception {
		return getJavaProject().findType(typeName);
	}
	
	/**
	 * Creates and returns a watchpoint
	 * 
	 * @param typeNmae type name
	 * @param fieldName field name
	 * @param access whether to suspend on field access
	 * @param modification whether to suspend on field modification
	 */	
	protected IJavaWatchpoint createWatchpoint(String typeName, String fieldName, boolean access, boolean modification) throws Exception {
		IType type = getType(typeName);
		return createWatchpoint(type, fieldName, access, modification);
	}
	
	/**
	 * Creates and returns an exception breakpoint
	 * 
	 * @param exName exception name
	 * @param caught whether to suspend in caught locations
	 * @param uncaught whether to suspend in uncaught locations
	 */	
	protected IJavaExceptionBreakpoint createExceptionBreakpoint(String exName, boolean caught, boolean uncaught) throws Exception {
		IType type = getType(exName);
		Map map = getExtraBreakpointAttributes(type);
		return JDIDebugModel.createExceptionBreakpoint(getBreakpointResource(type),exName, caught, uncaught, false, true, map);
	}
	
	/**
	 * Creates and returns a watchpoint
	 * 
	 * @param typeNmae type name
	 * @param fieldName field name
	 * @param access whether to suspend on field access
	 * @param modification whether to suspend on field modification
	 */	
/*	protected IJavaWatchpoint createWatchpoint(String typeName, String fieldName, boolean access, boolean modification) throws Exception {
		IType type = getType(typeName);
		return createWatchpoint(type, fieldName, access, modification);
	}*/


	/**
	 * Creates a WatchPoint on the field specified at the given path.
	 * Will create watchpoints on fields within anonymous types, inner types,
	 * local (non-public) types, and public types.  
	 * @param root
	 * @param packageName package name containing type to install breakpoint in, example "a.b.c"
	 * @param cuName simple compilation unit name within package, example "Something.java"
	 * @param fullTargetName - see below
	 * @param access whether to suspend on access 
	 * @param modification whether to suspend on modification
	 * @return a watchpoint
	 * @throws Exception
	 * @throws CoreException
	 * 
	 * @see
	 * </code>
	 * Syntax example:
	 * Type$InnerType$MethodNameAndSignature$AnonymousTypeDeclarationNumber$FieldName
	 * eg:<code>
	 * public class Foo{
	 * 		class Inner
	 * 		{
	 * 			public void aMethod()
	 * 			{
	 * 				Object anon = new Object(){
	 * 					int anIntField;
	 * 					String anonTypeMethod() {return "an Example";}				
	 * 				}
	 * 			}
	 * 		}
	 * }</code>
	 * To get the anonymous toString, syntax of fullTargetName would be: <code>Foo$Inner$aMethod()V$1$anIntField</code> 
	 */
	protected IJavaWatchpoint createNestedTypeWatchPoint(String root, String packageName, String cuName, 
			String fullTargetName, boolean access, boolean modification) throws Exception, CoreException {
		
		ICompilationUnit cunit = getCompilationUnit(getJavaProject(), root, packageName, cuName);
		IField field = (IField)getMember(cunit,fullTargetName);
		assertNotNull("Path to field is not valid", field);
		assertTrue("Field is not valid", field.exists());
		IType type = (IType)field.getParent();
		return createWatchpoint(type, field.getElementName(), access, modification);
	}
	

	/**
	 * Creates a watchpoint in a fully specified type (potentially non public).
	 * 
	 * @param packageName package name containing type to install breakpoint in, example "a.b.c"
	 * @param cuName simple compilation unit name within package, example "Something.java"
	 * @param typeName $ qualified type name within compilation unit, example "Something" or
	 *  "NonPublic" or "Something$Inner"
	 * @param fieldName name of the field
	 * @param access whether to suspend on access 
	 * @param modification whether to suspend on modification
	 */		
	protected IJavaWatchpoint createWatchpoint(String packageName, String cuName, String typeName, String fieldName, boolean access, boolean modification) throws Exception {
		IType type = getType(packageName, cuName, typeName);
		return createWatchpoint(type, fieldName, access, modification);
	}
	
	/**
	 * Creates a watchpoint on the specified field.
	 * 
	 * @param type type containing the field
	 * @param fieldName name of the field
	 * @param access whether to suspend on access
	 * @param modification whether to suspend on modification
	 * @return watchpoint
	 * @throws Exception
	 */
	protected IJavaWatchpoint createWatchpoint(IType type, String fieldName, boolean access, boolean modification) throws Exception, CoreException {
		assertNotNull("type not specified for watchpoint", type);
		IField field = type.getField(fieldName);
		Map map = getExtraBreakpointAttributes(field);
		IJavaWatchpoint wp = JDIDebugModel.createWatchpoint(getBreakpointResource(type), type.getFullyQualifiedName(), fieldName, -1, -1, -1, 0, true, map);
		wp.setAccess(access);
		wp.setModification(modification);
		return wp;
	}	
		
	/**
	 * Terminates the given thread and removes its launch
	 */
	protected void terminateAndRemove(IJavaThread thread) {
		if (thread != null) {
			terminateAndRemove((IJavaDebugTarget)thread.getDebugTarget());
		}
	}
	
	/**
	 * Terminates the given debug target and removes its launch.
	 * 
	 * NOTE: all breakpoints are removed, all threads are resumed, and then
	 * the target is terminated. This avoids defunct processes on linux.
	 */
	protected void terminateAndRemove(IJavaDebugTarget debugTarget) {
	    ILaunch launch = debugTarget.getLaunch();
		if (debugTarget != null && !(debugTarget.isTerminated() || debugTarget.isDisconnected())) {
			IPreferenceStore jdiUIPreferences = JDIDebugUIPlugin.getDefault().getPreferenceStore();
			jdiUIPreferences.setValue(IJDIPreferencesConstants.PREF_SUSPEND_ON_UNCAUGHT_EXCEPTIONS, false);
			
			DebugEventWaiter waiter = new DebugElementEventWaiter(DebugEvent.TERMINATE, debugTarget);
			try {
				removeAllBreakpoints();
				IThread[] threads = debugTarget.getThreads();
				for (int i = 0; i < threads.length; i++) {
					IThread thread = threads[i];
					try {
						if (thread.isSuspended()) {
							thread.resume();
						}
					} catch (CoreException e) {
					}
				}
				debugTarget.getDebugTarget().terminate();
				waiter.waitForEvent();
			} catch (CoreException e) {
			}
		}
		getLaunchManager().removeLaunch(launch);
        // ensure event queue is flushed
        DebugEventWaiter waiter = new DebugElementEventWaiter(DebugEvent.MODEL_SPECIFIC, this);
        DebugPlugin.getDefault().fireDebugEventSet(new DebugEvent[]{new DebugEvent(this, DebugEvent.MODEL_SPECIFIC)});
        waiter.waitForEvent();
	}
	
	/**
	 * Deletes all existing breakpoints
	 */
	protected void removeAllBreakpoints() {
		IBreakpoint[] bps = getBreakpointManager().getBreakpoints();
		try {
			getBreakpointManager().removeBreakpoints(bps, true);
		} catch (CoreException e) {
		}
	}
	
	/**
	 * Returns the first breakpoint the given thread is suspended
	 * at, or <code>null</code> if none.
	 * 
	 * @return the first breakpoint the given thread is suspended
	 * at, or <code>null</code> if none
	 */
	protected IBreakpoint getBreakpoint(IThread thread) {
		IBreakpoint[] bps = thread.getBreakpoints();
		if (bps.length > 0) {
			return bps[0];
		}
		return null;
	}
	
	/**
	 * Evaluates the given snippet in the context of the given stack frame and returns
	 * the result.
	 * 
	 * @param snippet code snippet
	 * @param frame stack frame context
	 * @return evaluation result
	 */
	protected IEvaluationResult evaluate(String snippet, IJavaStackFrame frame) throws Exception {
		DebugEventWaiter waiter= new DebugElementKindEventWaiter(DebugEvent.SUSPEND, IJavaThread.class);
		waiter.setTimeout(DEFAULT_TIMEOUT);
		
		IAstEvaluationEngine engine = EvaluationManager.newAstEvaluationEngine(getJavaProject(), (IJavaDebugTarget)frame.getDebugTarget());
		engine.evaluate(snippet, frame, this, DebugEvent.EVALUATION, true);

		Object suspendee= waiter.waitForEvent();
		setEventSet(waiter.getEventSet());
		assertNotNull("Program did not suspend.", suspendee);
		engine.dispose();
		return fEvaluationResult;
	}		
	
	/**
	 * @see IEvaluationListener#evaluationComplete(IEvaluationResult)
	 */
	public void evaluationComplete(IEvaluationResult result) {
		fEvaluationResult = result;
	}
	
	/**
	 * Performs a step over in the given stack frame and returns when complete.
	 * 
	 * @param frame stack frame to step in
	 */
	protected IJavaThread stepOver(IJavaStackFrame frame) throws Exception {
		DebugEventWaiter waiter= new DebugElementKindEventDetailWaiter(DebugEvent.SUSPEND, IJavaThread.class, DebugEvent.STEP_END);
		waiter.setTimeout(DEFAULT_TIMEOUT);
		
		frame.stepOver();
		
		Object suspendee= waiter.waitForEvent();
		setEventSet(waiter.getEventSet());
		assertNotNull("Program did not suspend.", suspendee);
		return (IJavaThread) suspendee;
	}

	/**
	 * Performs a step into in the given stack frame and returns when complete.
	 * 
	 * @param frame stack frame to step in
	 */
	protected IJavaThread stepInto(IJavaStackFrame frame) throws Exception {
		DebugEventWaiter waiter= new DebugElementKindEventDetailWaiter(DebugEvent.SUSPEND, IJavaThread.class, DebugEvent.STEP_END);
		waiter.setTimeout(DEFAULT_TIMEOUT);
		
		frame.stepInto();
		
		Object suspendee= waiter.waitForEvent();
		setEventSet(waiter.getEventSet());
		assertNotNull("Program did not suspend.", suspendee);
		return (IJavaThread) suspendee;		
	}
	
	/**
	 * Performs a step return in the given stack frame and returns when complete.
	 * 
	 * @param frame stack frame to step return from
	 */
	protected IJavaThread stepReturn(IJavaStackFrame frame) throws Exception {
		DebugEventWaiter waiter= new DebugElementKindEventDetailWaiter(DebugEvent.SUSPEND, IJavaThread.class, DebugEvent.STEP_END);
		waiter.setTimeout(DEFAULT_TIMEOUT);
		
		frame.stepReturn();
		
		Object suspendee= waiter.waitForEvent();
		setEventSet(waiter.getEventSet());
		assertNotNull("Program did not suspend.", suspendee);
		return (IJavaThread) suspendee;
	}	
	
	/**
	 * Performs a step into with filters in the given stack frame and returns when
	 * complete.
	 * 
	 * @param frame stack frame to step in
	 */
	protected IJavaThread stepIntoWithFilters(IJavaStackFrame frame) throws Exception {
		DebugEventWaiter waiter= new DebugElementKindEventWaiter(DebugEvent.SUSPEND, IJavaThread.class);
		waiter.setTimeout(DEFAULT_TIMEOUT);
		
		// turn filters on
		try {
			DebugUITools.setUseStepFilters(true);
			frame.stepInto();
		} finally {
			// turn filters off
			DebugUITools.setUseStepFilters(false);
		}
		
		
		Object suspendee= waiter.waitForEvent();
		setEventSet(waiter.getEventSet());
		assertNotNull("Program did not suspend.", suspendee);
		return (IJavaThread) suspendee;		
	}	

	/**
	 * Performs a step return with filters in the given stack frame and returns when
	 * complete.
	 * 
	 * @param frame stack frame to step in
	 */
	protected IJavaThread stepReturnWithFilters(IJavaStackFrame frame) throws Exception {
		DebugEventWaiter waiter= new DebugElementKindEventWaiter(DebugEvent.SUSPEND, IJavaThread.class);
		waiter.setTimeout(DEFAULT_TIMEOUT);
		
		// turn filters on
		try {
			DebugUITools.setUseStepFilters(true);
			frame.stepReturn();
		} finally {
			// turn filters off
			DebugUITools.setUseStepFilters(false);
		}
		
		
		Object suspendee= waiter.waitForEvent();
		setEventSet(waiter.getEventSet());
		assertNotNull("Program did not suspend.", suspendee);
		return (IJavaThread) suspendee;		
	}	
	
	/**
	 * Performs a step over with filters in the given stack frame and returns when
	 * complete.
	 * 
	 * @param frame stack frame to step in
	 */
	protected IJavaThread stepOverWithFilters(IJavaStackFrame frame) throws Exception {
		DebugEventWaiter waiter= new DebugElementKindEventWaiter(DebugEvent.SUSPEND, IJavaThread.class);
		waiter.setTimeout(DEFAULT_TIMEOUT);
		
		// turn filters on
		try {
			DebugUITools.setUseStepFilters(true);
			frame.stepOver();
		} finally {
			// turn filters off
			DebugUITools.setUseStepFilters(false);
		}
		
		
		Object suspendee= waiter.waitForEvent();
		setEventSet(waiter.getEventSet());
		assertNotNull("Program did not suspend.", suspendee);
		return (IJavaThread) suspendee;		
	}

	/**
	 * Returns the compilation unit with the given name.
	 * 
	 * @param project the project containing the CU
	 * @param root the name of the source folder in the project
	 * @param pkg the name of the package (empty string for default package)
	 * @param name the name of the CU (ex. Something.java)
	 * @return compilation unit
	 */
	protected ICompilationUnit getCompilationUnit(IJavaProject project, String root, String pkg, String name) {
		IProject p = project.getProject();
		IResource r = p.getFolder(root);
		return project.getPackageFragmentRoot(r).getPackageFragment(pkg).getCompilationUnit(name);
	}
	
    /**
     * Wait for builds to complete
     */
    public static void waitForBuild() {
        boolean wasInterrupted = false;
        do {
            try {
                Platform.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, null);
                Platform.getJobManager().join(ResourcesPlugin.FAMILY_MANUAL_BUILD, null);
                wasInterrupted = false;
            } catch (OperationCanceledException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                wasInterrupted = true;
            }
        } while (wasInterrupted);
    }	
    
    
    protected IJavaVariable findVariable(IJavaStackFrame frame, String name) throws DebugException {
        IJavaVariable variable = frame.findVariable(name);
        if (variable == null) {
            // dump visible variables
            IDebugModelPresentation presentation = DebugUIPlugin.getModelPresentation();
            System.out.println("Could not find variable '" + name + "' in frame: " + presentation.getText(frame));
            System.out.println("Visible variables are:");
            IVariable[] variables = frame.getVariables();
            for (int i = 0; i < variables.length; i++) {
                IVariable variable2 = variables[i];
                System.out.println("\t" + presentation.getText(variable2));
            }
            if (!frame.isStatic()) {
                variables = frame.getThis().getVariables();
                for (int i = 0; i < variables.length; i++) {
                    IVariable variable2 = variables[i];
                    System.out.println("\t" + presentation.getText(variable2));
                }
            }
        }
        return variable;
    }
	
	protected boolean isFileSystemCaseSensitive() {
		return Platform.OS_MACOSX.equals(Platform.getOS()) ? false : new File("a").compareTo(new File("A")) != 0; //$NON-NLS-1$ //$NON-NLS-2$
	}
}

