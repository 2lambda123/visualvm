/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.graalvm.visualvm.lib.jfluid.results.memory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.graalvm.visualvm.lib.jfluid.ProfilerClient;
import org.graalvm.visualvm.lib.jfluid.client.ClientUtils;
import org.graalvm.visualvm.lib.jfluid.results.CCTNode;
import org.graalvm.visualvm.lib.jfluid.utils.StringUtils;
import org.graalvm.visualvm.lib.jfluid.utils.formatting.MethodNameFormatterFactory;
import org.graalvm.visualvm.lib.jfluid.results.ExportDataDumper;
import java.util.ResourceBundle;
import org.graalvm.visualvm.lib.jfluid.results.FilterSortSupport;
import org.graalvm.visualvm.lib.jfluid.utils.Wildcards;


/**
 * Presentation-Time Memory Profiling Calling Context Tree (CCT) Node. Used "as is" for Object Allocation
 * profiling, and used as a base class for PresoObjLivenessCCTNode. Contains additional functionality
 * to map jmethodIDs (integer identifiers automatically assigned to methods by the JVM, that are returned
 * by stack trace routines) to method names. This includes sending a request to the server to get method
 * names/signatures for given jmethodIDs.
 *
 * @author Tomas Hurka
 * @author Misha Dmitriev
 */
public class PresoObjAllocCCTNode extends CCTNode {
    //~ Static fields/initializers -----------------------------------------------------------------------------------------------

    public static final String VM_ALLOC_CLASS = "org.graalvm.visualvm.lib.jfluid.server.ProfilerRuntimeMemory"; // NOI18N
    public static final String VM_ALLOC_METHOD = "traceVMObjectAlloc"; // NOI18N
    private static final String VM_ALLOC_TEXT = ResourceBundle.getBundle("org.graalvm.visualvm.lib.jfluid.results.memory.Bundle") // NOI18N
    .getString("PresoObjAllocCCTNode_VMAllocMsg"); // NOI18N
    private static final String UKNOWN_NODENAME = ResourceBundle.getBundle("org.graalvm.visualvm.lib.jfluid.results.memory.Bundle") // NOI18N
    .getString("PresoObjAllocCCTNode_UnknownMsg"); // NOI18N
    public static final int SORT_BY_NAME = 1;
    public static final int SORT_BY_ALLOC_OBJ_SIZE = 2;
    public static final int SORT_BY_ALLOC_OBJ_NUMBER = 3;
    
//    protected static final char MASK_FILTERED_NODE = 0x8;

    //~ Instance fields ----------------------------------------------------------------------------------------------------------

    public long nCalls;
    public long totalObjSize;
    public PresoObjAllocCCTNode parent;
    String className;
    String methodName;
//    String methodSig;
    String nodeName;
    public PresoObjAllocCCTNode[] children;
    int methodId;
    JMethodIdTable.JMethodIdTableEntry entry;
    
    protected char flags;

    //~ Constructors -------------------------------------------------------------------------------------------------------------
    
    public static PresoObjAllocCCTNode rootNode(PresoObjAllocCCTNode[] children) {
        PresoObjAllocCCTNode root = new PresoObjAllocCCTNode();
        root.setChildren(children);
        return root;
    }
    
    public PresoObjAllocCCTNode(String className, long nCalls, long totalObjSize) {
        this.className = className;
        this.nCalls = nCalls;
        this.totalObjSize = totalObjSize;
        
        methodName = Wildcards.ALLWILDCARD;
    }
    
    PresoObjAllocCCTNode() {}
    
    protected PresoObjAllocCCTNode(RuntimeMemoryCCTNode rtNode) {
        methodId = rtNode.methodId;

        if (rtNode instanceof RuntimeObjAllocTermCCTNode) {
            RuntimeObjAllocTermCCTNode rtTermNode = (RuntimeObjAllocTermCCTNode) rtNode;
            nCalls += rtTermNode.nCalls;
            totalObjSize += rtTermNode.totalObjSize;
        }
    }
    
    
    protected final void setChildren(PresoObjAllocCCTNode[] children) {
        this.children = children;
        for (PresoObjAllocCCTNode child : children) child.parent = this;
    }
    
    
    // --- Filtering support
    
    public CCTNode createFilteredNode() {
        PresoObjAllocCCTNode filtered = new PresoObjAllocCCTNode();
        setupFilteredNode(filtered);        
        return filtered;
    }
    
    protected void setupFilteredNode(PresoObjAllocCCTNode filtered) {
        filtered.setFilteredNode();
         
        filtered.parent = parent;

        filtered.nCalls = nCalls;
        filtered.totalObjSize = totalObjSize;

        Collection<PresoObjAllocCCTNode> _childrenL = resolveChildren(this);
        filtered.children = _childrenL.toArray(new PresoObjAllocCCTNode[0]);
    }
    
    public void merge(CCTNode node) {
        if (node instanceof PresoObjAllocCCTNode) {
            PresoObjAllocCCTNode _node = (PresoObjAllocCCTNode)node;
            
            nCalls += _node.nCalls;
            totalObjSize += _node.totalObjSize;

            List<PresoObjAllocCCTNode> ch = new ArrayList();
            
            // Include current children
            if (children != null) ch.addAll(Arrays.asList(children));
            
            // Add or merge new children
            for (PresoObjAllocCCTNode child : resolveChildren(_node)) {
                int idx = ch.indexOf(child);
                if (idx == -1) ch.add(child);
                else ch.get(idx).merge(child);
            }
            
            children = ch.toArray(new PresoObjAllocCCTNode[0]);
        }
    }

    protected static Collection<PresoObjAllocCCTNode> resolveChildren(PresoObjAllocCCTNode node) {
        PresoObjAllocCCTNode[] chldrn = (PresoObjAllocCCTNode[])node.getChildren();
        return chldrn == null ? Collections.EMPTY_LIST : Arrays.asList(chldrn);
    }
    
    // ---

    //~ Methods ------------------------------------------------------------------------------------------------------------------

    public static void getNamesForMethodIdsFromVM(ProfilerClient profilerClient, RuntimeMemoryCCTNode[] allStackRoots)
                                           throws ClientUtils.TargetAppOrVMTerminated {
        if (allStackRoots == null) {
            return; // Can happen if this is called too early
        }

        JMethodIdTable table = profilerClient.getJMethodIdTable();
        for (RuntimeMemoryCCTNode allStackRoot : allStackRoots) {
            if (allStackRoot != null) {
                checkMethodIdForNodeFromVM(table, allStackRoot);
            }
        }
        table.getNamesForMethodIds(profilerClient);
    }

    public static PresoObjAllocCCTNode createPresentationCCTFromSnapshot(JMethodIdTable methodIdTable,
                                                                         RuntimeMemoryCCTNode rootRuntimeNode,
                                                                         String classTypeName) {
        PresoObjAllocCCTNode rootNode = generateMirrorNode(rootRuntimeNode);
        assignNamesToNodesFromSnapshot(methodIdTable, rootNode, classTypeName);

        return rootNode;
    }

    public static PresoObjAllocCCTNode createPresentationCCTFromVM(ProfilerClient profilerClient,
                                                                   RuntimeMemoryCCTNode rootRuntimeNode, String classTypeName)
        throws ClientUtils.TargetAppOrVMTerminated {
        PresoObjAllocCCTNode rootNode = generateMirrorNode(rootRuntimeNode);
        assignNamesToNodesFromVM(profilerClient, rootNode, classTypeName);

        return rootNode;
    }

    public CCTNode getChild(int index) {
        if (index < children.length) {
            return children[index];
        } else {
            return null;
        }
    }

    public CCTNode[] getChildren() {
        return children;
    }

    public int getIndexOfChild(Object child) {
        for (int i = 0; i < children.length; i++) {
            if ((PresoObjAllocCCTNode) child == children[i]) {
                return i;
            }
        }

        return -1;
    }

    public String[] getMethodClassNameAndSig() {
        return new String[] { getClassName(), getMethodName(), getMethodSig()};
    }

    public int getNChildren() {
        if (children != null) {
            return children.length;
        } else {
            return 0;
        }
    }

    public String getNodeName() {
        if (nodeName == null) {
            if (isFiltered()) {
                nodeName = FilterSortSupport.FILTERED_OUT_LBL;
            } else if (methodId != 0) {
                if (VM_ALLOC_CLASS.equals(getClassName()) && VM_ALLOC_METHOD.equals(getMethodName())) { // special handling of ProfilerRuntimeMemory.traceVMObjectAlloc
                    nodeName = VM_ALLOC_TEXT;
                } else {
                    nodeName = MethodNameFormatterFactory.getDefault().getFormatter().formatMethodName(
                                        getClassName(), getMethodName(), getMethodSig()).toFormatted();
                }
            } else if (getClassName() != null) {
                nodeName = getClassName();
            } else {
                nodeName = UKNOWN_NODENAME;
            }
        }
        
        return nodeName;
    }

    public CCTNode getParent() {
        return parent;
    }

    public void sortChildren(int sortBy, boolean sortOrder) {
//        int nChildren = getNChildren();
//
//        if (nChildren == 0) {
//            return;
//        }
//
//        for (int i = 0; i < nChildren; i++) {
//            children[i].sortChildren(sortBy, sortOrder);
//        }
//
//        if (nChildren > 1) {
//            switch (sortBy) {
//                case SORT_BY_NAME:
//                    sortChildrenByName(sortOrder);
//
//                    break;
//                case SORT_BY_ALLOC_OBJ_SIZE:
//                    sortChildrenByAllocObjSize(sortOrder);
//
//                    break;
//                case SORT_BY_ALLOC_OBJ_NUMBER:
//                    sortChildrenByAllocObjNumber(sortOrder);
//
//                    break;
//            }
//        }
    }

    public String toString() {
        return getNodeName();
    }
    
//    public void setFilteredNode() {
//        flags |= MASK_FILTERED_NODE;
//    }
//    
//    public void resetFilteredNode() {
//        flags &= ~MASK_FILTERED_NODE;
//    }
//
//    public boolean isFilteredNode() {
//        return (flags & MASK_FILTERED_NODE) != 0;
//    }
    
//    void merge(PresoObjAllocCCTNode node) {
//        nCalls += node.nCalls;
//        totalObjSize += totalObjSize;
//        
//        if (node.children != null) {
//            for (PresoObjAllocCCTNode ch : node.children)
//                ch.parent = this;
//            
//            int chl = children == null ? 0 : children.length;
//            int newchl = node.children.length;
//            PresoObjAllocCCTNode[] newch = new PresoObjAllocCCTNode[chl + newchl];
//            if (children != null) System.arraycopy(children, 0, newch, 0, chl);
//            System.arraycopy(node.children, 0, newch, chl, newchl);
//            children = newch;
//        }
//    }
    
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof PresoObjAllocCCTNode)) return false;
        PresoObjAllocCCTNode other = (PresoObjAllocCCTNode)o;
        if (isFiltered()) {
            return other.isFiltered();
        }
        if (other.isFiltered()) {
            return false;
        }
        if (methodId == 0) {
            return getNodeName().equals(other.getNodeName());
        }
        if (other.methodId == 0) {
            return false;
        }
        return entry.className.equals(other.entry.className) &&
               entry.methodName.equals(other.entry.methodName) &&
               entry.methodSig.equals(other.entry.methodSig);
    }
    
    public int hashCode() {
        if (methodId == 0 || isFiltered()) {
            return getNodeName().hashCode();
        }
        return entry.className.hashCode() ^ entry.methodName.hashCode() ^ entry.methodSig.hashCode();
    }

    protected static void assignNamesToNodesFromSnapshot(JMethodIdTable methodIdTable, PresoObjAllocCCTNode rootNode,
                                                         String classTypeName) {
        rootNode.className = StringUtils.userFormClassName(classTypeName);
        rootNode.setFullClassAndMethodInfo(methodIdTable);
    }

    protected static void assignNamesToNodesFromVM(ProfilerClient profilerClient, PresoObjAllocCCTNode rootNode,
                                                   String classTypeName)
                                            throws ClientUtils.TargetAppOrVMTerminated {
        JMethodIdTable table = profilerClient.getJMethodIdTable();
        table.getNamesForMethodIds(profilerClient);
        rootNode.className = StringUtils.userFormClassName(classTypeName);
        rootNode.setFullClassAndMethodInfo(table);
    }

    protected static PresoObjAllocCCTNode generateMirrorNode(RuntimeMemoryCCTNode rtNode) {
        PresoObjAllocCCTNode thisNode = new PresoObjAllocCCTNode(rtNode);
        Object nodeChildren = rtNode.children;

        if (nodeChildren != null) {
            if (nodeChildren instanceof RuntimeMemoryCCTNode) {
                thisNode.children = new PresoObjAllocCCTNode[1];

                PresoObjAllocCCTNode child = generateMirrorNode((RuntimeMemoryCCTNode) nodeChildren);
                thisNode.children[0] = child;
                child.parent = thisNode;
                thisNode.nCalls += child.nCalls;
                thisNode.totalObjSize += child.totalObjSize;
            } else {
                RuntimeMemoryCCTNode[] ar = (RuntimeMemoryCCTNode[]) nodeChildren;
                int nChildren = ar.length;

                if (nChildren > 0) {
                    thisNode.children = new PresoObjAllocCCTNode[nChildren];

                    for (int i = 0; i < nChildren; i++) {
                        PresoObjAllocCCTNode child = generateMirrorNode(ar[i]);
                        thisNode.children[i] = child;
                        child.parent = thisNode;
                        thisNode.nCalls += child.nCalls;
                        thisNode.totalObjSize += child.totalObjSize;
                    }
                }
            }
        }

        return thisNode;
    }

    protected boolean setFullClassAndMethodInfo(JMethodIdTable methodIdTable) {
        if (methodId != 0) {
            entry = methodIdTable.getEntry(methodId);
        }

        // If any object allocations that happen in our own code are caught (which shouldn't happen),
        // make sure to conceal this data here.
        boolean thisNodeOk = entry!=null && !"org/graalvm/visualvm/lib/jfluid/server/ProfilerServer".equals(entry.className); // NOI18N
        boolean childrenOk = true;

        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                if (!children[i].setFullClassAndMethodInfo(methodIdTable)) {
                    childrenOk = false;
                    children[i] = null;
                }
            }
        }

        if (!childrenOk) {
            // Determine the number of non-null children and create a new children array
            int newLen = 0;

            for (PresoObjAllocCCTNode children1 : children) {
                newLen += ((children1 != null) ? 1 : 0);
            }

            boolean hasNonNullChildren = (newLen > 0);

            if (!hasNonNullChildren) {
                children = null;
            } else {
                PresoObjAllocCCTNode[] newChildren = new PresoObjAllocCCTNode[newLen];
                int idx = 0;

                for (PresoObjAllocCCTNode children1 : children) {
                    if (children1 != null) {
                        newChildren[idx++] = children1;
                    }
                }

                children = newChildren;
            }

            if ((getMethodName() == null) || (getMethodName().equals("main") && getMethodSig() // NOI18N
                .equals("([Ljava/lang/String;)V"))) { // NOI18N

                return true;
            } else {
                return thisNodeOk;
            }
        } else {
            return thisNodeOk;
        }
    }

    protected static void checkMethodIdForNodeFromVM(JMethodIdTable table, RuntimeMemoryCCTNode rtNode) {
        if (rtNode.methodId != 0) {
            table.checkMethodId(rtNode.methodId);
        }

        Object nodeChildren = rtNode.children;

        if (nodeChildren != null) {
            if (nodeChildren instanceof RuntimeMemoryCCTNode) {
                checkMethodIdForNodeFromVM(table, (RuntimeMemoryCCTNode) nodeChildren);
            } else {
                RuntimeMemoryCCTNode[] ar = (RuntimeMemoryCCTNode[]) nodeChildren;

                for (RuntimeMemoryCCTNode ar1 : ar) {
                    checkMethodIdForNodeFromVM(table, ar1);
                }
            }
        }
    }

//    protected void sortChildrenByAllocObjNumber(boolean sortOrder) {
//        int len = children.length;
//        long[] values = new long[len];
//
//        for (int i = 0; i < len; i++) {
//            values[i] = children[i].nCalls;
//        }
//
//        sortLongs(values, sortOrder);
//    }
//
//    protected void sortChildrenByAllocObjSize(boolean sortOrder) {
//        int len = children.length;
//        long[] values = new long[len];
//
//        for (int i = 0; i < len; i++) {
//            values[i] = children[i].totalObjSize;
//        }
//
//        sortLongs(values, sortOrder);
//    }
//
//    protected void sortChildrenByName(boolean sortOrder) {
//        int len = children.length;
//        String[] values = new String[len];
//
//        for (int i = 0; i < len; i++) {
//            values[i] = children[i].getNodeName();
//        }
//
//        sortStrings(values, sortOrder);
//    }
//
//    protected void sortFloats(float[] values, boolean sortOrder) {
//        int len = values.length;
//
//        // Just the insertion sort - we will never get really large arrays here
//        for (int i = 0; i < len; i++) {
//            for (int j = i; (j > 0) && ((sortOrder == false) ? (values[j - 1] < values[j]) : (values[j - 1] > values[j])); j--) {
//                float tmp = values[j];
//                values[j] = values[j - 1];
//                values[j - 1] = tmp;
//
//                PresoObjAllocCCTNode tmpCh = children[j];
//                children[j] = children[j - 1];
//                children[j - 1] = tmpCh;
//            }
//        }
//    }
//
//    protected void sortInts(int[] values, boolean sortOrder) {
//        int len = values.length;
//
//        // Just the insertion sort - we will never get really large arrays here
//        for (int i = 0; i < len; i++) {
//            for (int j = i; (j > 0) && ((sortOrder == false) ? (values[j - 1] < values[j]) : (values[j - 1] > values[j])); j--) {
//                int tmp = values[j];
//                values[j] = values[j - 1];
//                values[j - 1] = tmp;
//
//                PresoObjAllocCCTNode tmpCh = children[j];
//                children[j] = children[j - 1];
//                children[j - 1] = tmpCh;
//            }
//        }
//    }
//
//    protected void sortLongs(long[] values, boolean sortOrder) {
//        int len = values.length;
//
//        // Just the insertion sort - we will never get really large arrays here
//        for (int i = 0; i < len; i++) {
//            for (int j = i; (j > 0) && ((sortOrder == false) ? (values[j - 1] < values[j]) : (values[j - 1] > values[j])); j--) {
//                long tmp = values[j];
//                values[j] = values[j - 1];
//                values[j - 1] = tmp;
//
//                PresoObjAllocCCTNode tmpCh = children[j];
//                children[j] = children[j - 1];
//                children[j - 1] = tmpCh;
//            }
//        }
//    }
//
//    protected void sortStrings(String[] values, boolean sortOrder) {
//        int len = values.length;
//
//        // Just the insertion sort - we will never get really large arrays here
//        for (int i = 0; i < len; i++) {
//            for (int j = i;
//                     (j > 0)
//                     && ((sortOrder == false) ? (values[j - 1].compareTo(values[j]) < 0) : (values[j - 1].compareTo(values[j]) > 0));
//                     j--) {
//                String tmp = values[j];
//                values[j] = values[j - 1];
//                values[j - 1] = tmp;
//
//                PresoObjAllocCCTNode tmpCh = children[j];
//                children[j] = children[j - 1];
//                children[j - 1] = tmpCh;
//            }
//        }
//    }

    public void exportXMLData(ExportDataDumper eDD,String indent) {
        String newline = System.getProperty("line.separator"); // NOI18N
        StringBuffer result = new StringBuffer(indent+"<Node>"+newline); //NOI18N
        result.append(indent).append(" <Name>").append(replaceHTMLCharacters(getNodeName())).append("<Name>").append(newline); //NOI18N
        result.append(indent).append(" <Parent>").append(replaceHTMLCharacters((getParent()==null)?("none"):(((PresoObjAllocCCTNode)getParent()).getNodeName()))).append("<Parent>").append(newline); //NOI18N
        result.append(indent).append(" <Bytes_Allocated>").append(totalObjSize).append("</Bytes_Allocated>").append(newline); //NOI18N
        result.append(indent).append(" <Objects_Allocated>").append(nCalls).append("</Objects_Allocated>").append(newline); //NOI18N
        eDD.dumpData(result); //dumps the current row
        // children nodes
        if (children!=null) {
            for (int i = 0; i < getNChildren(); i++) {
                children[i].exportXMLData(eDD, indent+" "); //NOI18N
            }
        }
        result=new StringBuffer(indent+"</Node>"); //NOI18N
        eDD.dumpData(result);
    }

    public void exportHTMLData(ExportDataDumper eDD, int depth) {
        StringBuffer result = new StringBuffer("<tr><td class=\"method\"><pre class=\"method\">"); //NOI18N
        for (int i=0; i<depth; i++) {
            result.append("."); //NOI18N
        }
        result.append(replaceHTMLCharacters(getNodeName())).append("</pre></td><td class=\"right\">").append(totalObjSize).append("</td><td class=\"right\">").append(nCalls).append("</td><td class=\"parent\"><pre class=\"parent\">").append(replaceHTMLCharacters((getParent()==null)?("none"):(((PresoObjAllocCCTNode)getParent()).getNodeName()))).append("</pre></td></tr>"); //NOI18N
        eDD.dumpData(result); //dumps the current row
        // children nodes
        if (children!=null) {
            for (PresoObjAllocCCTNode children1 : children) {
                children1.exportHTMLData(eDD, depth+1);
            }
        }
    }

    private String replaceHTMLCharacters(String s) {
        StringBuilder sb = new StringBuilder();
        int len = s.length();
        for (int i = 0; i < len; i++) {
          char c = s.charAt(i);
          switch (c) {
              case '<': sb.append("&lt;"); break; // NOI18N
              case '>': sb.append("&gt;"); break; // NOI18N
              case '&': sb.append("&amp;"); break; // NOI18N
              case '"': sb.append("&quot;"); break; // NOI18N
              default: sb.append(c); break;
          }
        }
        return sb.toString();
    }

    public void exportCSVData(String separator, int depth, ExportDataDumper eDD) {
        StringBuffer result = new StringBuffer();
        String newLine = "\r\n"; // NOI18N
        String quote = "\""; // NOI18N
        String indent = " "; // NOI18N

        // this node
        result.append(quote);
        for (int i=0; i<depth; i++) {
            result.append(indent); // to simulate the tree structure in CSV
        }
        result.append(getNodeName()).append(quote).append(separator);
        result.append(quote).append(totalObjSize).append(quote).append(separator);
        result.append(quote).append(nCalls).append(quote).append(separator);
        result.append(quote).append((getParent()==null)?("none"):(((PresoObjAllocCCTNode)getParent()).getNodeName())).append(newLine); // NOI18N
        eDD.dumpData(result); //dumps the current row
        // children nodes
        if (children!=null) {
            for (PresoObjAllocCCTNode children1 : children) {
                children1.exportCSVData(separator, depth+1, eDD);
            }
        }
    }

    String getClassName() {
        if (className == null && entry != null) {
            className = entry.className.replace('/', '.'); // NOI18N
        }
        return className;
    }

    String getMethodName() {
        if (methodName == null && entry != null) {
            methodName = entry.methodName;
            if (entry.isNative) {
                methodName = methodName.concat(JMethodIdTable.NATIVE_SUFFIX);
            }   
        }
        return methodName;
    }

    String getMethodSig() {
        if (entry != null) {
            return entry.methodSig;
        }
        return null;
    }
    
    static class Handle {

        final PresoObjAllocCCTNode node;
        
        Handle(PresoObjAllocCCTNode n) {
            node = n;
        }

        public int hashCode() {
            return node.hashCode();
        }

        public boolean equals(Object obj) {
            if (obj == this) return true;
            
            return node.equals(((Handle)obj).node);
        }
        
        
        
    }
}
