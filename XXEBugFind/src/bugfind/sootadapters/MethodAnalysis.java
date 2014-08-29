/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package bugfind.sootadapters;

import bugfind.xxe.MethodParameterValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLInputFactory;
import soot.ArrayType;
import soot.Hierarchy;
import soot.Local;
import soot.NullType;
import soot.PrimType;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.dava.toolkits.base.AST.interProcedural.ConstantFieldValueFinder;
import soot.jimple.Constant;
import soot.jimple.IntConstant;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;
import soot.jimple.internal.JArrayRef;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JInstanceFieldRef;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JNewExpr;
import soot.jimple.internal.JStaticInvokeExpr;
import soot.jimple.internal.JVirtualInvokeExpr;
import soot.jimple.internal.JimpleLocal;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.CombinedAnalysis;
import soot.toolkits.scalar.CombinedDUAnalysis;

/**
 * As the name of the class implies, this class does analyses of some method.
 * @author Mikosh
 */
public class MethodAnalysis {
    public static final int CALLED_BEFORE = -1, CALLED_AFTER = 1, CALLED_SAME_TIME = 0;
    public static final int INDETERMINABLE_ARGUMENT_VALUES = -99, SAME_ARGUMENT_VALUES = -78, DIFF_ARGUMENT_VALUES = -45;
    private static final int START = 0;
    private CallGraphObject cgo;
    private CallGraph callGraph;

    /**
     * Creates a MethodAnalysis object from a CallGraphObject and a Soot CallGraph
     * @param cgo
     * @param callGraph 
     */
    public MethodAnalysis(CallGraphObject cgo, CallGraph callGraph) {
        this.cgo = cgo;
        this.callGraph = callGraph;
    }

    /**
     * Determines if a method is called  in another method
     * @param targetCaller the method that should contain (ie call) the callee
     * @param callee the method to be called
     * @return true if callee is called in targetCaller and false otherwise
     */
    public boolean isCalledInMethod(SootMethod targetCaller, SootMethod callee) {
        List<CallSite> listCS = this.cgo.getCallSites(callGraph, callee);
        for (CallSite cs : listCS) {
            if (cs.getSourceMethod().getSignature().equals(targetCaller.getSignature())) {
                return true;
            }
        }
        
        return false;
    }
    
   
    
    public int compare(SootMethod callerMethod, SootMethod callee1, SootMethod callee2) {
        if (!isCalledInMethod(callerMethod, callee1)) {
            throw new RuntimeException("Specified callee method not called in specified soot caller method.\nDetail: "
                    + callee1 + " is never called in " + callerMethod);            
        }
        if (!isCalledInMethod(callerMethod, callee2)) {
            throw new RuntimeException("Specified callee method not called in specified soot caller method.\nDetail: "
                    + callee2 + " is never called in " + callerMethod);            
        }
        
        List<CallSite> listCS1 = cgo.getCallSitesInMethod(callGraph, callee1, callerMethod);
        Collections.sort(listCS1);
        List<CallSite> listCS2 = cgo.getCallSitesInMethod(callGraph, callee2, callerMethod);
        Collections.sort(listCS2);
        
        if (listCS1.get(0).getLineLocation() < listCS2.get(0).getLineLocation()) {
            return CALLED_BEFORE;
        }
        else if (listCS1.get(0).getLineLocation() > listCS2.get(0).getLineLocation()) {
            return CALLED_AFTER;
        }
        else if (listCS1.get(0).getLineLocation() == listCS2.get(0).getLineLocation()) {
            boolean b = SimpleIntraDataFlowAnalysis.isBefore(callerMethod, listCS1.get(0).getEdge().srcStmt(), 
                    listCS2.get(0).getEdge().srcStmt());
            return (b) ? CALLED_BEFORE : CALLED_AFTER;
            //throw new RuntimeException("both are on same line. try to get which statement is run first on line");
            //return CALLED_SAME_TIME;
        }
        else {
            throw new RuntimeException("Code should not reach here");
        }
    }
    
    @Deprecated
    public List<CallSite> getAllCallsBefore(SootMethod callerMethod, SootMethod calleeToBeBefore, SootMethod callee2, Unit callee2Location) {
        if (!isCalledInMethod(callerMethod, calleeToBeBefore)) {
            throw new RuntimeException("Specified callee method not called in specified soot caller method.\nDetail: "
                    + calleeToBeBefore + " is never called in " + callerMethod);            
        }
        if (!isCalledInMethod(callerMethod, callee2)) {
            throw new RuntimeException("Specified callee method not called in specified soot caller method.\nDetail: "
                    + callee2 + " is never called in " + callerMethod);            
        }
        
        List<CallSite> listCBefore = cgo.getCallSitesInMethod(callGraph, calleeToBeBefore, callerMethod);
        Collections.sort(listCBefore);
        List<CallSite> listCS2 = cgo.getCallSitesInMethod(callGraph, callee2, callerMethod);
        Collections.sort(listCS2);
        
        Iterator<CallSite> ite = listCBefore.iterator();
        
        while (ite.hasNext()) {
            CallSite cs = ite.next();
            
            if (cs.getLineLocation() >= listCS2.get(0).getLineLocation()) {// remeber to account for same line
                ite.remove();
            }
        }
        
        return listCBefore;
    }
    
    public List<CallSite> getAllCallsBefore(SootMethod callerMethod, SootMethod calleeToBeBefore, CallSite targetCS) {
        if (!isCalledInMethod(callerMethod, calleeToBeBefore)) {
            throw new RuntimeException("Specified callee method not called in specified soot caller method.\nDetail: "
                    + calleeToBeBefore + " is never called in " + callerMethod);            
        }
        
        if (!targetCS.getSourceMethod().getSignature().equals(callerMethod.getSignature())) {
            throw new RuntimeException("Specified call site [" + targetCS + "] is not in the specified caller method " + callerMethod);
        }

        List<CallSite> listCBefore = cgo.getCallSitesInMethod(callGraph, calleeToBeBefore, callerMethod);
        Collections.sort(listCBefore);
        
        int loc = targetCS.getLineLocation();
        
        Iterator<CallSite> ite = listCBefore.iterator();
        
        while (ite.hasNext()) {
            CallSite cs = ite.next();
            
            if (cs.getLineLocation() > loc) {// remeber to account for same line
                ite.remove();
            }
            else if (cs.getLineLocation() == loc) {
                boolean isBefore = SimpleIntraDataFlowAnalysis.isBefore(callerMethod, cs.getEdge().srcStmt(), 
                        targetCS.getEdge().srcStmt());
                if (!isBefore) {
                    ite.remove();
                }
            }
        }
        
        return listCBefore;
    }
    
    public List<CallSite> getAllCallsBefore(SootMethod callerMethod, SootMethod calleeToBeBefore, Unit targetUnit) {
        List<CallSite> listCBefore = cgo.getCallSitesInMethod(callGraph, calleeToBeBefore, callerMethod);
        Collections.sort(listCBefore);
        
        //int loc = targetCS.getLineLocation();
        
        Iterator<CallSite> ite = listCBefore.iterator();
        
        while (ite.hasNext()) {
            CallSite cs = ite.next();
            
            boolean isBefore = SimpleIntraDataFlowAnalysis.isBefore(callerMethod, cs.getEdge().srcStmt(),
                    targetUnit);
            if (!isBefore) {
                ite.remove();
            }            
        }
        
        return listCBefore;
    }

    public void setCallGraph(CallGraph callGraph) {
        this.callGraph = callGraph;
    }

    /**
     * Gets the call graph
     * @return 
     */
    public CallGraph getCallGraph() {
        return callGraph;
    }

    /**
     * Converts a JArrayRef object to a Variable
     * @param ar the JArrayRef object to convert
     * @return a Variable corresponding to ar
     */
    protected Variable convertToVariable(JArrayRef ar) {        
        Type arrayRefType = ar.getType();
        String typeName = null;
        
        if (arrayRefType instanceof PrimType) {
            typeName = ((PrimType) arrayRefType).getClass().getName();
        } else if (arrayRefType instanceof RefType) {
            typeName = ((RefType) arrayRefType).getClassName();
        } else if (arrayRefType instanceof NullType) {
            typeName = null; //NSOT//((NullType)localvartype).getClassName();
        } else if (arrayRefType instanceof ArrayType) {
            typeName = arrayRefType.toString();
        } else {
            throw new RuntimeException("Currently cannot support variable of type " + arrayRefType);
        }

        Value base = ar.getBase();
        int varType = 0;
        String varName = null;
        
        if (base instanceof JimpleLocal) {
            varName = ((JimpleLocal)base).getName() + "[" + ar.getIndex() + "]";
            varType = Variable.LOCAL_VARIABLE;
        }
        else if (base instanceof JInstanceFieldRef) {            
            JInstanceFieldRef fr = (JInstanceFieldRef) base;
            SootField sf = fr.getField();
            varName = sf.getName() + "[" + ar.getIndex() + "]";            
            varType = (sf.isStatic()) ? Variable.STATIC_VARIABLE
                    : Variable.FIELD_VARIABLE;
        }
        else if (base instanceof SootField) {
            SootField sf = (SootField) base;
            varName = sf.getName() + "[" + ar.getIndex() + "]";
            varType = (sf.isStatic()) ? Variable.STATIC_VARIABLE
                    : Variable.FIELD_VARIABLE;
        }        
        
        Variable retVal = new Variable(varName, typeName, varType);
        return retVal;
    }
    
    /**
     * Convets the specified Soot Value object to a Variable object
     * @param v the soot value to be converted
     * @return the converted Variable form of v 
     */
    protected Variable convertToVariable(Value v) {
        if (v instanceof JimpleLocal) {
            JimpleLocal jl = (JimpleLocal) v;

            Type localvartype = v.getType();

            String typeName = null;
            if (localvartype instanceof PrimType) {
                typeName = ((PrimType) localvartype).getClass().getName();
            } else if (localvartype instanceof RefType) {
                typeName = ((RefType) localvartype).getClassName();
            } else if (localvartype instanceof NullType) {
                typeName = null; //NSOT//((NullType)localvartype).getClassName();
            } else if (localvartype instanceof ArrayType) {
                typeName = localvartype.toString();
            } else {
                throw new RuntimeException("Currently cannot support variable of type " + localvartype);
            }
            Variable retVal = new Variable(jl.getName(), typeName, Variable.LOCAL_VARIABLE);
            return retVal;
        } else if (v instanceof JInstanceFieldRef) {
            JInstanceFieldRef fr = (JInstanceFieldRef) v;

            SootField sf = fr.getField();

            Type sootfieldType = v.getType();

            String typeName = null;
            if (sootfieldType instanceof PrimType) {
                typeName = ((PrimType) sootfieldType).getClass().getName();
            } else if (sootfieldType instanceof RefType) {
                typeName = ((RefType) sootfieldType).getClassName();
            } else if (sootfieldType instanceof NullType) {
                typeName = null; //NSOT//((NullType)localvartype).getClassName();
            } else if (sootfieldType instanceof ArrayType) {
                typeName = sootfieldType.toString();
            } else {
                throw new RuntimeException("Currently cannot support variable of type " + sootfieldType);
            }

            Variable retVal = new Variable(sf.getName(), typeName, (sf.isStatic()) ? Variable.STATIC_VARIABLE
                    : Variable.FIELD_VARIABLE);
            return retVal;
        } else if (v instanceof SootField) {
            SootField sf = (SootField) v;

            Type sootfieldType = v.getType();

            String typeName = null;
            if (sootfieldType instanceof PrimType) {
                typeName = ((PrimType) sootfieldType).getClass().getName();
            } else if (sootfieldType instanceof RefType) {
                typeName = ((RefType) sootfieldType).getClassName();
            } else if (sootfieldType instanceof NullType) {
                typeName = null; //NSOT//((NullType)localvartype).getClassName();
            } else if (sootfieldType instanceof ArrayType) {
                typeName = sootfieldType.toString();
            } else {
                throw new RuntimeException("Currently cannot support variable of type " + sootfieldType);
            }

            Variable retVal = new Variable(sf.getName(), typeName, (sf.isStatic()) ? Variable.STATIC_VARIABLE
                    : Variable.FIELD_VARIABLE);
            return retVal;
        } else if (v instanceof JArrayRef) {
            JArrayRef jar = (JArrayRef) v;
            Value av = jar.getBase();
            return convertToVariable(av);
        } else {
            throw new RuntimeException("Currently cannot support value " + v);
        }
    }
    
    public Variable getDefinedVariable(JAssignStmt stmt) {
        Value v = stmt.getLeftOp();        
        return convertToVariable(v);        
    }
    
    public Variable getInvokedVariable(JAssignStmt stmt) {
        //first ensure statement is non static
        ensureNonStaticExpr(stmt);
        
        Value v = stmt.getRightOp(); 
        
        if (v instanceof JVirtualInvokeExpr) {
            return getInvokedVariable((JVirtualInvokeExpr)v);
        }
        else {
            return convertToVariable(v);
        }
    }
    
    public Variable getInvokedVariable(JInvokeStmt stmt) {       
        //first ensure statement is non static
        ensureNonStaticExpr(stmt);
        
        // the first use box of a jinvoke statement is the variable being used, others may be arguments
        Value v = ((ValueBox) stmt.getUseBoxes().get(0)).getValue();//(Value) stmt.getUseBoxes().get(0); 
        return convertToVariable(v);
    }
    
    protected Variable getInvokedVariable(JVirtualInvokeExpr invokeExpr) {       
        // the first use box of a jinvoke expression is the variable being used, others may be arguments
        Value v = ((ValueBox) invokeExpr.getUseBoxes().get(0)).getValue();
        return convertToVariable(v);
    }
    
    public int compareArguments(CallSite cs, List<MethodParameterValue> listMPV) {
        Edge edge = cs.getEdge();
        Stmt stmt = edge.srcStmt();
        if (!stmt.containsInvokeExpr()) {
            throw new RuntimeException("Edge statement does not contain an invoke expression. ie no method call in statement "
                    + "corresponding to edge");
        }
        
        List<Value> argumentList = stmt.getInvokeExpr().getArgs();
        if (argumentList.size() != listMPV.size()) {
            throw new RuntimeException("The method arguments are not compatible. ");            
        }
        
        //SootMethod calleeMeth = edge.tgt();
        
        int comparison = START;
        for (int i=0; i<argumentList.size(); ++i) {
            Value v = argumentList.get(i);
            MethodParameterValue mpv = listMPV.get(i);
            
            if (v.getType().toString().equals(mpv.getType())) { // ensure they are similar types before comparison
                if (v instanceof Constant) {// if it is instance of constant
                    Constant c = (Constant) v;
                    if (!c.toString().equals(mpv.getValue())) {//if (c.getType().toString().equals(mpv.getType())) {// if same type, check if values are the same                        
                        comparison = DIFF_ARGUMENT_VALUES;
                        break;
                    }
                } 
                else {// otherwise it is variable                    
                    ValueString paramVal = resolveValue(v, cs);
                    if (paramVal == null || paramVal.getValue() == null) {// ie could not resolve value of the variavle
                        comparison = INDETERMINABLE_ARGUMENT_VALUES;
                        break;
                    }
                    
                    if (!paramVal.getValue().equals(mpv.getValue())) {// if values are not equal
                        comparison = DIFF_ARGUMENT_VALUES;
                        break;
                    }
                    
                }
            } 
            else if (v instanceof Constant && (mpv.getType().equals("boolean"))) {
                //  A special case for boolean constant as soot jimple converts it to int (ie no boolean type)
                // and so will will make type comparisons false
                // check if the original parameter type is boolean from method signature
                Constant c = (Constant) v;
                int aval = Integer.parseInt(c.toString());
                String boolVal = converIntToBoolean(aval);
                if (!boolVal.equals(mpv.getValue())) {
                    comparison = DIFF_ARGUMENT_VALUES;
                    break;
                }
            }            
            else {
                // check whether 
                Hierarchy hierach = Scene.v().getActiveHierarchy();
                SootClass mpvTypeSC = Scene.v().getSootClass(mpv.getType());
                SootClass vSC = Scene.v().getSootClass(v.getType().toString());
                
                if ((!mpvTypeSC.isInterface() && hierach.isClassSubclassOf(vSC, mpvTypeSC)) 
                        || (mpvTypeSC.isInterface() && hierach.isInterfaceSubinterfaceOf(vSC, mpvTypeSC))
                        || (mpvTypeSC.isInterface() && vSC.implementsInterface(mpv.getType()))) {
                    MethodParameterValue mpv2 = new MethodParameterValue(vSC.getName(), mpv.getValue());
                    
                    ValueString paramVal = resolveValue(v, cs);
                    if (paramVal == null) {// ie could not resolve value of the variavle
                        comparison = INDETERMINABLE_ARGUMENT_VALUES;
                        break;
                    }
                    if (!paramVal.getValue().equals(mpv.getValue())) {// if values are not equal
                        comparison = DIFF_ARGUMENT_VALUES;
                        break;
                    }
                    
                }               
                else {
                    // throw exception
                    throw new RuntimeException("The method specified at the given callsite differs from the one compared with. "
                            + "Details:\nArgument " + (i) + " differs. Expected parameter type is " + mpv.getType()
                            + " while argument type " + v.getType() + " was found at Call site");
                }
            }
        }
        
        if (comparison == START) { // if comparison remains at start, it means there was no problem and all the arguments are equal
            comparison = SAME_ARGUMENT_VALUES;
        }
        
        return comparison;
    }
    
    protected final String converIntToBoolean(int val) {
        return (val == 0) ? "false" : "true";
    }
    
    public boolean isAssignedTo(SootMethod parentMethod, String assignerClassName, String assigneeClassName) {
        Iterator<Unit> iteUnits = parentMethod.getActiveBody().getUnits().iterator();
        
        while (iteUnits.hasNext()) {
            Unit unit = iteUnits.next();
            if (unit instanceof JAssignStmt) {
                JAssignStmt stmt = (JAssignStmt) unit;
                
                if (stmt.containsInvokeExpr()) {
                    Variable assigneeVar = getDefinedVariable(stmt);
                    if (assigneeVar.getType().equals(assigneeClassName)) {
                        //System.out.println("assigner typename: " + stmt.getRightOp().getType().g);
                        ValueBox vb = (ValueBox) stmt.getUseBoxes().get(0);
                        
                        if (((RefType)vb.getValue().getType()).getClassName().equals(assignerClassName)) {
                            return true;
                        }                        
                    }
                }
            } 
            
        }
        
        return false;
    }
    
    public Variable getAssignerVariable(SootMethod parentMethod, String assignerClassName, String assigneeClassName) {
        Iterator<Unit> iteUnits = parentMethod.getActiveBody().getUnits().iterator();
        
        while (iteUnits.hasNext()) {
            Unit unit = iteUnits.next();
            if (unit instanceof JAssignStmt) {
                JAssignStmt stmt = (JAssignStmt) unit;
                Variable assigneeVar = getDefinedVariable(stmt);
                if (stmt.containsInvokeExpr()) {
                    if (assigneeVar.getType().equals(assigneeClassName)) {
                        //System.out.println("assigner typename: " + stmt.getRightOp().getType().g);
                        ValueBox vb = (ValueBox) stmt.getUseBoxes().get(0);
                        
                        if (((RefType)vb.getValue().getType()).getClassName().equals(assignerClassName)) {
                            return getInvokedVariable(stmt);
                        }                        
                    }
                }
                       else if (stmt.getRightOp() instanceof JNewExpr) {// if instance of new expression, just return the left var
                    // ie AClassB b = new AClassB(); // ie return b, since b is not assigned to any other variable
                    return assigneeVar;
                }
            } 
            
        }
        
        return null;
    }
    
    public Variable getInvokedVariable(Stmt stmt) {
        if (stmt instanceof JInvokeStmt) {
            return getInvokedVariable((JInvokeStmt)stmt);
        }
        else if (stmt instanceof JAssignStmt) {
            return getInvokedVariable((JAssignStmt)stmt);
        }
        else {
            throw new RuntimeException("Statement type not supported. The statement not an assign or an invoke statement");
        }
    }
    
    public boolean isInvokedOnSameVariable(Stmt stmt1, Stmt stmt2) {
        Variable var1 = getInvokedVariable(stmt1);
        Variable var2 = getInvokedVariable(stmt2);
        if (var1.getName() == null || var2.getName() == null) {
            return false;
        }
        else {
            return (var1.getName().equals(var2.getName()));
        }          
    }
    
    public List<CallSite> filterByVariable(List<CallSite> listCS, Variable filter) {
        
        if (filter == null) {
            throw new RuntimeException("The variable filter given cannot be null");
        }
        
//        List<CallSite> retListCS = new ArrayList<>(listCS.size());
//        
//        for (CallSite cs: listCS) {
//            retListCS.add(cs);
//        }
        
        Iterator<CallSite> ite = listCS.iterator();        
        
        while (ite.hasNext()) {
            CallSite cs = ite.next();
            Stmt stmt = cs.getEdge().srcStmt();
            
            Variable vCS = getInvokedVariable(stmt);
            
            if (vCS == null) {
                ite.remove();
                continue;
            }
            if (!vCS.getName().equals(filter.getName()) || !vCS.getType().equals(filter.getType())) {
                ite.remove();                
            }
        }
        
        return listCS;
    }
    
    protected String constantToString(Constant c, boolean isBoolean) {
       
        if (isBoolean && c instanceof IntConstant) {
            if (!c.toString().equals("0") && !c.toString().equals("0")) {
                throw new RuntimeException("cannot convert int value " + c + " to boolean");
            }
            return (c.toString().equals("0")) ? "false" : "true";
                    
        }
        
        return c.toString();
    }
    
    /**
     * Tries to resolve the value of a soot Value object (a variable eg local variable) and returns a value string. Should be used for simple types 
     * like String, int , float, etc. This method may have to walk up the method call chain to retrieve the value 
     * if it has been defined some where in the code. For instance, when the value is a parameter/argument to a method 
     * eg. str in <code> aMethod(String str); </code>, this method tries to resolve the value by looking for call sites
     * of aMethod and deduce the values that was passed in such call sites. If the call sites are more than one, this
     * method return null meaning it cannot determine the value as there are more than one value definition and this 
     * method should return just one. Another case it returns null is if the value can only be determined at runtime. 
     * For instance if going up the call chain leads to the static entry point main(args) method
     * 
     * @param value the value to be resolved
     * @param cs the call site where the value is being used
     * @return the value string corresponding to the value at the call site or null if it cannot be determined    
     * @see  MethodAnalysis.resolvePossibleValues
     */
    protected ValueString resolveValue(Value value, CallSite cs) {
        Variable v = convertToVariable(value);
        
        Local localrepr = getSootValue(v, cs.getSourceMethod());
        if (localrepr == null) {       
            throw new RuntimeException("Cannot resolve variable " + v + " in edge "
                        + cs.getEdge() + " with src-method " + cs.getSourceMethod());           
        }
        
        UnitGraph uv = new ExceptionalUnitGraph(cs.getSourceMethod().getActiveBody());
        CombinedAnalysis ca = CombinedDUAnalysis.v(uv);
        List<Unit> list = ca.getDefsOfAt(localrepr, cs.getEdge().srcStmt());
        
        if (list.isEmpty()) {
            if (SimpleIntraDataFlowAnalysis.isParameterLocal(cs.getSourceMethod(), localrepr)) {
                List<CallSite> lst = cgo.getCallSites(callGraph, cs.getSourceMethod());
                if (lst.size() > 1) {
                    return null;
                }
                else {
                    Stmt stmt = lst.get(0).getEdge().srcStmt();
                    Value val = stmt.getInvokeExpr().getArg(
                            SimpleIntraDataFlowAnalysis.getParameterLocalIndex(lst.get(0).getSourceMethod(), localrepr));
                    return resolveValue(val, lst.get(0));
                }
            }
            return null;
        }
        
        if (list.size() > 1) {
            System.out.println("warning: number defsofvar > " + 1);
        }
        
        JAssignStmt stmt = (JAssignStmt) list.get(list.size()-1);        
        Value rightOp = stmt.getRightOp();
        
        if (rightOp instanceof Constant) {
            Constant constnt = (Constant) stmt.getRightOp();
            String valstr = constantToString(constnt, v.getType().equals("boolean"));
            
            return new ValueString(v.getType(), v.getName(), valstr);
        }
        else if (rightOp instanceof StaticFieldRef) {
            StaticFieldRef srf = (StaticFieldRef) stmt.getRightOp();
            SootField f = srf.getField();//SootFieldRef sf = srf.getFieldRef();;
            String type = srf.getType().toString();
            String name = f.getDeclaringClass().getName() + "." + f.getName();
            if (f.isFinal()) {// if it is final, then the value never changes
                return new ValueString(type, name, name);
            }
            else {// the actual value may be changed elsewhere
                
                return new ValueString(type, name, null);//edge.srcStmt().getTags()//ConstantFieldValueFinder//edge.src().getActiveBody(); SootU
            }            
        }
        else if (rightOp instanceof JInstanceFieldRef) {
            JInstanceFieldRef jfr = (JInstanceFieldRef) rightOp;
            Value vv = jfr.getBase();
            return null; 
            
        }
        else if (rightOp instanceof JimpleLocal) {
            return resolveValue(rightOp, cs);
        }
        else {// currently doesnt handle fieldref and static values
            return null;
        }
        
        
    }
    
    public List<ValueString> resolvePossibleValues(Value value, CallSite cs) {        
        List<ValueString> listVals = new ArrayList<>();
        List<Edge> listEdges = new ArrayList<>();
        return resolvePossibleValues(value, cs, listVals, listEdges);
    }
    
    protected List<ValueString> resolvePossibleValues(Value value, CallSite cs, List<ValueString> listVS, 
            List<Edge> calTraceEdges) {
        Variable v = convertToVariable(value);
        
        Local localrepr = getSootValue(v, cs.getSourceMethod());
        if (localrepr == null) {
            throw new RuntimeException("Cannot resolve variable " + v + " in edge "
                        + cs.getEdge() + " with src-method " + cs.getSourceMethod());            
        }
        
        UnitGraph uv = new ExceptionalUnitGraph(cs.getSourceMethod().getActiveBody());
        CombinedAnalysis ca = CombinedDUAnalysis.v(uv);
        List<Unit> list = ca.getDefsOfAt(localrepr, cs.getEdge().srcStmt());
        if (list.isEmpty()) {
            if (SimpleIntraDataFlowAnalysis.isParameterLocal(cs.getSourceMethod(), localrepr)) {
                List<CallSite> lst = cgo.getCallSites(callGraph, cs.getSourceMethod());
                if (lst.size() > 1) {
                    for(CallSite aCS : lst) {
                        if (!calTraceEdges.contains(aCS.getEdge())) {
                            resolvePossibleValues(value, aCS, listVS, calTraceEdges);
                        }
                    }
                    return listVS;
                }
                else {
                    Stmt stmt = lst.get(0).getEdge().srcStmt();
                    Value val = stmt.getInvokeExpr().getArg(
                            SimpleIntraDataFlowAnalysis.getParameterLocalIndex(lst.get(0).getSourceMethod(), localrepr));
                    ValueString vs = resolveValue(val, lst.get(0));
                    listVS.add(vs);
                    
                    return listVS;
                }
            }
            return listVS;
        }
        
        if (list.size() > 1) {
            System.out.println("warning: number defsofvar > " + 1);
        }
        
        JAssignStmt stmt = (JAssignStmt) list.get(list.size()-1);        
        Value rightOp = stmt.getRightOp();
        
        if (rightOp instanceof Constant) {
            Constant constnt = (Constant) stmt.getRightOp();
            String valstr = constantToString(constnt, v.getType().equals("boolean"));
            
            ValueString vs = new ValueString(v.getType(), v.getName(), valstr);
            listVS.add(vs);
            return listVS;
        }
        else if (rightOp instanceof StaticFieldRef) {
            StaticFieldRef srf = (StaticFieldRef) stmt.getRightOp();
            SootField f = srf.getField();//SootFieldRef sf = srf.getFieldRef();;
            String type = srf.getType().toString();
            String name = f.getDeclaringClass().getName() + "." + f.getName();
            if (f.isFinal()) {// if it is final, then the value never changes
                ValueString vs = new ValueString(type, name, name);
                listVS.add(vs);
                return listVS;
            }
            else {// the actual value may be changed elsewhere
                
                ValueString vs = new ValueString(type, name, null);//edge.srcStmt().getTags()//ConstantFieldValueFinder//edge.src().getActiveBody(); SootU
                listVS.add(vs);
                return listVS;
            }            
        }
        else if (rightOp instanceof JInstanceFieldRef) {
            JInstanceFieldRef jfr = (JInstanceFieldRef) rightOp;
            Value vv = jfr.getBase();
            return null; 
            
        }
        else if (rightOp instanceof JimpleLocal) {
            ValueString vs = resolveValue(rightOp, cs);
            listVS.add(vs);
            return listVS;
        }
        else {// currently doesnt handle fieldref and static values
            return listVS;
        }
        
    }
    
    /**
     * converts the variable definition in the specified method into a soot Local object
     * @param v the variable definition
     * @param parentMethod the parentMethod
     * @return a local corresponding to the specified variable in the specified method or null if it 
     * cannot be determined
     */
    public Local getSootValue(Variable v, SootMethod parentMethod) {
        Iterator<Local> ite =  parentMethod.getActiveBody().getLocals().iterator();
        while (ite.hasNext()) {
            Local localvar = ite.next();
            if (localvar.getName().equals(v.getName()) && localvar.getType().toString().equals(v.getType())) {
                return localvar;
            }
        }
        
        return null;
    }
    
    protected int getCallType(Edge edge) {
        throw new UnsupportedOperationException("not supported yet");
    }
    
    protected void ensureNonStaticExpr(Stmt stmt) {
        if (!stmt.containsInvokeExpr()) {
            throw new RuntimeException("This statement is not an invoke expression");
        }
        if (stmt instanceof JStaticInvokeExpr) {
            throw new RuntimeException("this is a static statement");
        }
    }
    
    
}
