/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.debug.tests.refactoring;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.debug.core.IJavaClassPrepareBreakpoint;
import org.eclipse.jdt.debug.core.IJavaLineBreakpoint;
import org.eclipse.jdt.debug.core.IJavaMethodBreakpoint;
import org.eclipse.jdt.debug.core.IJavaWatchpoint;
import org.eclipse.jdt.debug.tests.AbstractDebugTest;
import org.eclipse.jdt.internal.corext.refactoring.rename.JavaRenameProcessor;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameCompilationUnitProcessor;
import org.eclipse.ltk.core.refactoring.CreateChangeOperation;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.RenameRefactoring;

public class RenameCompilationUnitUnitTests extends AbstractDebugTest{

	public RenameCompilationUnitUnitTests(String name) {
		super(name);
	}

	protected void cleanTestFiles() throws Exception
	{
		//cleanup Movee
		IFile target = getJavaProject().getProject().getFile("src/a/b/Movee.java");//move up a dir
		if(target.exists())
			target.delete(false, false, null);		
		target = getJavaProject().getProject().getFile("src/a/b/c/Movee.java");//move up a dir
		if(target.exists())
			target.delete(false, false, null);
		//get original source & replace old result
		IFile source = getJavaProject().getProject().getFile("src/a/b/c/MoveeSource");//no .java - it's a bin
		source.copy(target.getFullPath(), false, null );
		
		//cleanup child
		target = getJavaProject().getProject().getFile("src/a/b/c/MoveeChild.java");//move up a dir
		if(target.exists())
			target.delete(false, false, null);
		//get original source & replace old result
		source = getJavaProject().getProject().getFile("src/a/b/c/MoveeChildSource");//no .java - it's a bin
		source.copy(target.getFullPath(), false, null );
	}

	protected final void performRefactor(final Refactoring refactoring) throws Exception {
		if(refactoring==null)
			return;
		CreateChangeOperation create= new CreateChangeOperation(refactoring);
		refactoring.checkFinalConditions(new NullProgressMonitor());
		PerformChangeOperation perform= new PerformChangeOperation(create);
		ResourcesPlugin.getWorkspace().run(perform, new NullProgressMonitor());//maybe SubPM?
		waitForBuild();
	}
	
	/**
	 * @param src
	 * @param pack
	 * @param cunit
	 * @param fullTargetName
	 * @param targetLineage
	 * @throws Exception
	 */
	private void runClassLoadBreakpointTest(String src, String pack, String cunit, String fullTargetName, String targetLineage) throws Exception {
		cleanTestFiles();		
		try {
			//create breakpoint to test
			IJavaClassPrepareBreakpoint breakpoint = createClassPrepareBreakpoint(src, pack, cunit, fullTargetName);
			//refactor
			Refactoring ref = setupRefactor(src, pack, cunit);
			performRefactor(ref);
			//test breakpoints
			IBreakpoint[] breakpoints = getBreakpointManager().getBreakpoints();
			assertEquals("wrong number of breakpoints", 1, breakpoints.length);
			breakpoint = (IJavaClassPrepareBreakpoint) breakpoints[0];
			assertTrue("Breakpoint Marker has ceased existing",breakpoint.getMarker().exists());
			assertEquals("breakpoint attached to wrong type", targetLineage, breakpoint.getTypeName());
		} catch (Exception e) {
			throw e;
		} finally {
			removeAllBreakpoints();
		}
	}
	
	/**
	 * @param src
	 * @param pack
	 * @param cunit
	 * @param fullTargetName
	 * @param targetsParentName
	 * @param lineNumber
	 * @throws Exception
	 */
	private void runLineBreakpointTest(String src, String pack, String cunit, String fullTargetName, String targetsParentName, int lineNumber) throws Exception {
		cleanTestFiles();
		try {
			//create breakpoint to test
			IJavaLineBreakpoint breakpoint = createLineBreakpoint(lineNumber, src, pack, cunit, fullTargetName);
			//refactor
			Refactoring ref = setupRefactor(src, pack, cunit);
			performRefactor(ref);
			//test breakpoints
			IBreakpoint[] breakpoints = getBreakpointManager().getBreakpoints();
			assertEquals("wrong number of breakpoints", 1, breakpoints.length);
			breakpoint = (IJavaLineBreakpoint) breakpoints[0];
			assertTrue("Breakpoint Marker has ceased existing",breakpoint.getMarker().exists());
			assertEquals("breakpoint attached to wrong type", pack+"."+targetsParentName, breakpoint.getTypeName());
			assertEquals("breakpoint on wrong line", lineNumber, breakpoint.getLineNumber());
		} catch (Exception e) {
			throw e;
		} finally {
			removeAllBreakpoints();
		}
	}
	
	/**
	 * @param src
	 * @param pack
	 * @param cunit
	 * @param fullTargetName
	 * @param targetLineage
	 * @param methodName
	 * @throws Exception
	 */
	private void runMethodBreakpointTest(String src, String pack, String cunit, String fullTargetName, String targetLineage, String methodName) throws Exception {
		cleanTestFiles();
		try {
			//create breakpoint to test
			IJavaMethodBreakpoint breakpoint = createMethodBreakpoint(src, pack, cunit,fullTargetName, true, false);
			//refactor
			Refactoring ref = setupRefactor(src, pack, cunit);
			performRefactor(ref);
			//test breakpoints
			IBreakpoint[] breakpoints = getBreakpointManager().getBreakpoints();
			assertEquals("wrong number of breakpoints", 1, breakpoints.length);
			breakpoint = (IJavaMethodBreakpoint) breakpoints[0];
			assertTrue("Breakpoint Marker has ceased existing",breakpoint.getMarker().exists());
			assertEquals("wrong type name", targetLineage, breakpoint.getTypeName());
			assertEquals("breakpoint attached to wrong method",methodName,breakpoint.getMethodName());
		} catch (Exception e) {
			throw e;
		} finally {
			removeAllBreakpoints();
		}
	}

	/**
	 * @param src
	 * @param pack
	 * @param cunit
	 * @param fullTargetName
	 * @param targetLineage
	 * @param fieldName
	 * @throws Exception
	 */
	private void runWatchPointTest(String src, String pack, String cunit, String fullTargetName, String targetLineage, String fieldName) throws Exception {
		cleanTestFiles();		
		try {
			//create breakpoint to test
			IJavaWatchpoint breakpoint = createNestedTypeWatchPoint(src, pack, cunit, fullTargetName, true, true);
			//refactor
			Refactoring ref = setupRefactor(src, pack, cunit);
			performRefactor(ref);
			//test breakpoints
			IBreakpoint[] breakpoints = getBreakpointManager().getBreakpoints();
			assertEquals("wrong number of breakpoints", 1, breakpoints.length);
			breakpoint = (IJavaWatchpoint) breakpoints[0];
			assertTrue("Breakpoint Marker has ceased existing",breakpoint.getMarker().exists());
			assertEquals("breakpoint attached to wrong type", targetLineage, breakpoint.getTypeName());
			assertEquals("breakpoint attached to wrong field", fieldName, breakpoint.getFieldName());
		} catch (Exception e) {
			throw e;
		} finally {
			removeAllBreakpoints();
		}
	}
	
	private Refactoring setupRefactor(String root, String packageName, String cuName) throws Exception {
		
		IJavaProject javaProject = getJavaProject();
		ICompilationUnit cunit = getCompilationUnit(javaProject, root, packageName, cuName);
				
		JavaRenameProcessor proc = new RenameCompilationUnitProcessor(cunit);
		proc.setNewElementName("RenamedCompilationUnit.java");
			
		RenameRefactoring ref= new RenameRefactoring(proc);
		
		//setup final refactoring conditions
		RefactoringStatus refactoringStatus= ref.checkAllConditions(new NullProgressMonitor());
		if(!refactoringStatus.isOK())
		{
			System.out.println(refactoringStatus.getMessageMatchingSeverity(refactoringStatus.getSeverity()));
			return null;
		}		
		
		return ref;
	}
	
	//////////////////////////////////////////////////////////////////////////////////////	
	
	public void testInnerAnonmyousTypeClassLoadpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "MoveeChild$InnerChildType$innerChildsMethod()V$1",
					targetLineage = pack+"."+"MoveeChild$InnerChildType$1";
			runClassLoadBreakpointTest(src, pack, cunit, fullTargetName, targetLineage);
	}//end testBreakPoint	

	public void testInnerAnonymousTypeLineBreakpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "MoveeChild$InnerChildType$innerChildsMethod()V$1",
					targetLineage = pack+"."+"MoveeChild$InnerChildType$1";
			int lineNumber = 40;
			
			runLineBreakpointTest(src, pack, cunit, fullTargetName, targetLineage, lineNumber);
	}//end testBreakPoint	
	
	public void testInnerAnonymousTypeMethodBreakpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "MoveeChild$InnerChildType$innerChildsMethod()V$1$anonTypeMethod()V",
					targetLineage = pack+"."+"MoveeChild$InnerChildType$1",
					methodName = "anonTypeMethod";
			runMethodBreakpointTest(src, pack, cunit, fullTargetName, targetLineage, methodName);
	}//end testBreakPoint
	
	public void testInnerAnonymousTypeWatchpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "MoveeChild$InnerChildType$innerChildsMethod()V$1$anAnonInt",
					targetLineage = pack+"."+"MoveeChild$InnerChildType$1",
					fieldName = "anAnonInt";
						
			runWatchPointTest(src, pack, cunit, fullTargetName, targetLineage, fieldName);
	}//end testBreakPoint	
	
	public void testInnerClassLoadpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "MoveeChild$InnerChildType",
					targetLineage = pack+"."+"MoveeChild$InnerChildType";
	
			runClassLoadBreakpointTest(src, pack, cunit, fullTargetName, targetLineage);
	}//end testBreakPoint		
	
	public void testInnerLineBreakpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "MoveeChild$InnerChildType",
					targetLineage = pack+"."+"MoveeChild$InnerChildType";
			int lineNumber = 35;
			
			runLineBreakpointTest(src, pack, cunit, fullTargetName, targetLineage, lineNumber);
	}//end testBreakPoint	
	
	public void testInnerMethodBreakpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "MoveeChild$InnerChildType$innerChildsMethod()V",
					targetLineage = pack+"."+"MoveeChild$InnerChildType",
					methodName = "innerChildsMethod";
			runMethodBreakpointTest(src, pack, cunit, fullTargetName, targetLineage, methodName);
	}//end testBreakPoint	
	
	public void testInnerWatchpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "MoveeChild$InnerChildType$innerChildInt",
					targetLineage = pack+"."+"MoveeChild$InnerChildType",
					fieldName = "innerChildInt";
						
			runWatchPointTest(src, pack, cunit, fullTargetName, targetLineage, fieldName);
	}//end testBreakPoint		

	public void testNonPublicAnonymousTypeClassLoadpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "NonPublicChildType$nonPublicChildsMethod()V$1",
					targetLineage = pack+"."+"NonPublicChildType$1";
	
			runClassLoadBreakpointTest(src, pack, cunit, fullTargetName, targetLineage);
	}//end testBreakPoint		
	
	public void testNonPublicAnonymousTypeLineBreakpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "NonPublicChildType$nonPublicChildsMethod()V$1",
					targetLineage = pack+"."+"NonPublicChildType$1";
			int lineNumber = 56;
			
			runLineBreakpointTest(src, pack, cunit, fullTargetName, targetLineage, lineNumber);
	}//end testBreakPoint	
	
	public void testNonPublicAnonymousTypeMethodBreakpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "NonPublicChildType$nonPublicChildsMethod()V$1$anonTypeMethod()V",
					targetLineage = pack+"."+"NonPublicChildType$1",
					methodName = "anonTypeMethod";
			runMethodBreakpointTest(src, pack, cunit, fullTargetName, targetLineage, methodName);
	}//end testBreakPoint		
	
	public void testNonPublicAnonymousTypeWatchpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "NonPublicChildType$nonPublicChildsMethod()V$1$anAnonInt",
					targetLineage = pack+"."+"NonPublicChildType$1",
					fieldName = "anAnonInt";
						
			runWatchPointTest(src, pack, cunit, fullTargetName, targetLineage, fieldName);
	}//end testBreakPoint	
		
	public void testNonPublicClassLoadpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "NonPublicChildType",
					targetLineage = pack+"."+"NonPublicChildType";
	
					
			runClassLoadBreakpointTest(src, pack, cunit, fullTargetName, targetLineage);
	}//end testBreakPoint			
	
	public void testNonPublicLineBreakpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "NonPublicChildType",
					targetLineage = pack+"."+"NonPublicChildType";
			int lineNumber = 51;
			
			runLineBreakpointTest(src, pack, cunit, fullTargetName, targetLineage, lineNumber);
	}//end testBreakPoint		
	
	public void testNonPublicMethodBreakpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "NonPublicChildType$nonPublicChildsMethod()V$",
					targetLineage = pack+"."+"NonPublicChildType",
					methodName = "nonPublicChildsMethod";
			runMethodBreakpointTest(src, pack, cunit, fullTargetName, targetLineage, methodName);
	}//end testBreakPoint

	public void testNonPublicWatchpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "NonPublicChildType$nonPublicChildInt",
					targetLineage = pack+"."+"NonPublicChildType",
					fieldName = "nonPublicChildInt";
						
			runWatchPointTest(src, pack, cunit, fullTargetName, targetLineage, fieldName);
	}//end testBreakPoint	
	
	public void testPublicAnonymousTypeClassLoadpoint() throws Exception {
		
		String 	src = "src", 
		pack = "a.b.c",
		cunit = "MoveeChild.java",
		fullTargetName = "MoveeChild$childsMethod()V$1",
		targetLineage = pack+"."+"MoveeChild$1";

		runClassLoadBreakpointTest(src, pack, cunit, fullTargetName, targetLineage);
	}//end testBreakPoint		
	
	public void testPublicAnonymousTypeLineBreakpoint() throws Exception {
					
		String 	src = "src", 
		pack = "a.b.c",
		cunit = "MoveeChild.java",
		fullTargetName = "MoveeChild$childsMethod()V$1",
		targetLineage = "MoveeChild$1";
		int lineNumber = 26;
		
		runLineBreakpointTest(src, pack, cunit, fullTargetName, targetLineage, lineNumber);
	}//end testBreakPoint		
	
	public void testPublicAnonymousTypeMethodBreakpoint() throws Exception {
		String 	src = "src", 
		pack = "a.b.c",
		cunit = "MoveeChild.java",
		fullTargetName = "MoveeChild$childsMethod()V$1$anonTypeMethod()V",
		targetLineage = pack+"."+"MoveeChild$1",
		methodName = "anonTypeMethod";
		
		runMethodBreakpointTest(src, pack, cunit, fullTargetName, targetLineage, methodName);
	}//end testBreakPoint
			
	public void testPublicAnonymousTypeWatchpoint() throws Exception {
		String 	src = "src", 
		pack = "a.b.c",
		cunit = "MoveeChild.java",
		fullTargetName = "MoveeChild$childsMethod()V$1$anAnonInt",
		targetLineage = pack+"."+"MoveeChild$1",
		fieldName = "anAnonInt";
			
		runWatchPointTest(src, pack, cunit, fullTargetName, targetLineage, fieldName);
	}//end testBreakPoint		
	
	public void testPublicClassLoadpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "MoveeChild",
					targetLineage = pack+"."+"MoveeChild";
			runClassLoadBreakpointTest(src, pack, cunit, fullTargetName, targetLineage);
	}//end testBreakPoint		
	
public void testPublicLineBreakpoint() throws Exception {

			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "MoveeChild",
					targetLineage = "MoveeChild";
			int lineNumber = 21;
			
			runLineBreakpointTest(src, pack, cunit, fullTargetName, targetLineage, lineNumber);
	}//end testBreakPoint			
	
	public void testPublicMethodBreakpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "MoveeChild$childsMethod()V",
					targetLineage = pack+"."+"MoveeChild",
					methodName = "childsMethod";
			runMethodBreakpointTest(src, pack, cunit, fullTargetName, targetLineage, methodName);
	}//end testBreakPoint		
	
	public void testPublicWatchpoint() throws Exception {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "MoveeChild$aChildInt",
					targetLineage = pack+"."+"MoveeChild",
					fieldName = "aChildInt";
			runWatchPointTest(src, pack, cunit, fullTargetName, targetLineage, fieldName);
	}//end testBreakPoint			
	
}
