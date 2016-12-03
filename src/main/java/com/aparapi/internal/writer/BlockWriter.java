/**
 * Copyright (c) 2016 - 2017 Syncleus, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
Copyright (c) 2010-2011, Advanced Micro Devices, Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
following conditions are met:

Redistributions of source code must retain the above copyright notice, this list of conditions and the following
disclaimer.

Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
disclaimer in the documentation and/or other materials provided with the distribution.

Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products
derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

If you use the software (in whole or in part), you shall adhere to all applicable U.S., European, and other export
laws, including but not limited to the U.S. Export Administration Regulations ("EAR"), (15 C.F.R. Sections 730 through
774), and E.U. Council Regulation (EC) No 1334/2000 of 22 June 2000.  Further, pursuant to Section 740.6 of the EAR,
you hereby certify that, except pursuant to a license granted by the United States Department of Commerce Bureau of
Industry and Security or as otherwise permitted pursuant to a License Exception under the U.S. Export Administration
Regulations ("EAR"), you will not (1) export, re-export or release to a national of a country in Country Groups D:1,
E:1 or E:2 any restricted technology, software, or source code you receive hereunder, or (2) export to Country Groups
D:1, E:1 or E:2 the direct product of such technology or software, if such foreign produced direct product is subject
to national security controls as identified on the Commerce Control List (currently found in Supplement 1 to Part 774
of EAR).  For the most current Country Group listings, or for additional information about the EAR or your obligations
under those regulations, please refer to the U.S. Bureau of Industry and Security's website at http://www.bis.doc.gov/.

*/
package com.aparapi.internal.writer;

import com.aparapi.Config;
import com.aparapi.internal.exception.CodeGenException;
import com.aparapi.internal.instruction.BranchSet;
import com.aparapi.internal.instruction.Instruction;
import com.aparapi.internal.instruction.InstructionSet;
import com.aparapi.internal.model.ClassModel;
import com.aparapi.internal.model.Entrypoint;
import com.aparapi.internal.model.MethodModel;
import com.aparapi.*;

import java.util.*;

/**
 * Base abstract class for converting <code>Aparapi</code> IR to text.<br/>
 *
 *
 * @author gfrost
 *
 */

public abstract class BlockWriter{

   public final static String arrayLengthMangleSuffix = "__javaArrayLength";

   public final static String arrayDimMangleSuffix = "__javaArrayDimension";

   public abstract void write(String _string);

   public void writeln(String _string) {
      write(_string);
      newLine();
   }

   public int indent = 0;

   public void in() {
      indent++;
   }

   public void out() {
      indent--;
   }

   public void newLine() {
      write("\n");
      for (int i = 0; i < indent; i++) {
         write("   ");
      }
   }

   public void writeConditionalBranch16(InstructionSet.ConditionalBranch16 _branch16, boolean _invert) throws CodeGenException {

      if (_branch16 instanceof InstructionSet.If) {
         final InstructionSet.If iff = (InstructionSet.If) _branch16;

         writeInstruction(iff.getLhs());
         write(_branch16.getOperator().getText(_invert));
         writeInstruction(iff.getRhs());
      } else if (_branch16 instanceof InstructionSet.I_IFNULL) {
         final InstructionSet.I_IFNULL iff = (InstructionSet.I_IFNULL) _branch16;
         writeInstruction(iff.getFirstChild());

         if (_invert) {
            write(" != NULL");
         } else {
            write(" == NULL");
         }

      } else if (_branch16 instanceof InstructionSet.I_IFNONNULL) {
         final InstructionSet.I_IFNONNULL iff = (InstructionSet.I_IFNONNULL) _branch16;
         writeInstruction(iff.getFirstChild());

         if (_invert) {
            write(" == NULL");
         } else {
            write(" != NULL");
         }
      } else if (_branch16 instanceof InstructionSet.IfUnary) {
         final InstructionSet.IfUnary branch16 = (InstructionSet.IfUnary) _branch16;
         final Instruction comparison = branch16.getUnary();
         final InstructionSet.ByteCode comparisonByteCode = comparison.getByteCode();
         final String comparisonOperator = _branch16.getOperator().getText(_invert);

         switch (comparisonByteCode) {
            case FCMPG:
            case DCMPG:
            case FCMPL:
            case DCMPL:
               if (Config.verboseComparitor) {
                  write("/* bytecode=" + comparisonByteCode.getName() + " invert=" + _invert + "*/");
               }
               writeInstruction(comparison.getFirstChild());
               write(comparisonOperator);
               writeInstruction(comparison.getLastChild());
               break;
            default:
               if (Config.verboseComparitor) {
                  write("/* default bytecode=" + comparisonByteCode.getName() + " invert=" + _invert + "*/");
               }
               writeInstruction(comparison);
               write(comparisonOperator);
               write("0");
         }
      }
   }

   public void writeComposite(InstructionSet.CompositeInstruction instruction) throws CodeGenException {
      if (instruction instanceof InstructionSet.CompositeArbitraryScopeInstruction) {
         newLine();

         writeBlock(instruction.getFirstChild(), null);
      } else if (instruction instanceof InstructionSet.CompositeIfInstruction) {
         newLine();
         write("if (");
         final Instruction blockStart = writeConditional(instruction.getBranchSet());

         write(")");
         writeBlock(blockStart, null);
      } else if (instruction instanceof InstructionSet.CompositeIfElseInstruction) {
         newLine();
         write("if (");
         final Instruction blockStart = writeConditional(instruction.getBranchSet());
         write(")");
         Instruction elseGoto = blockStart;
         while (!(elseGoto.isBranch() && elseGoto.asBranch().isUnconditional())) {
            elseGoto = elseGoto.getNextExpr();
         }
         writeBlock(blockStart, elseGoto);
         write(" else ");
         writeBlock(elseGoto.getNextExpr(), null);
      } else if (instruction instanceof InstructionSet.CompositeForSunInstruction) {
         newLine();
         write("for (");
         Instruction topBranch = instruction.getFirstChild();
         if (topBranch instanceof InstructionSet.AssignToLocalVariable) {
            writeInstruction(topBranch);
            topBranch = topBranch.getNextExpr();
         }
         write("; ");
         final BranchSet branchSet = instruction.getBranchSet();
         final Instruction blockStart = writeConditional(branchSet);

         final Instruction lastGoto = instruction.getLastChild();

         if (branchSet.getFallThrough() == lastGoto) {
            // empty body no delta!
            write(";){}");
         } else {
            final Instruction delta = lastGoto.getPrevExpr();
            write("; ");
            if (!(delta instanceof InstructionSet.CompositeInstruction)) {
               writeInstruction(delta);
               write(")");
               writeBlock(blockStart, delta);
            } else {
               write("){");
               in();
               writeSequence(blockStart, delta);

               newLine();
               writeSequence(delta, delta.getNextExpr());
               out();
               newLine();
               write("}");

            }
         }

      } else if (instruction instanceof InstructionSet.CompositeWhileInstruction) {
         newLine();
         write("while (");
         final BranchSet branchSet = instruction.getBranchSet();
         final Instruction blockStart = writeConditional(branchSet);
         write(")");
         final Instruction lastGoto = instruction.getLastChild();
         writeBlock(blockStart, lastGoto);

      } else if (instruction instanceof InstructionSet.CompositeEmptyLoopInstruction) {
         newLine();
         write("for (");
         Instruction topBranch = instruction.getFirstChild();
         if (topBranch instanceof InstructionSet.AssignToLocalVariable) {
            writeInstruction(topBranch);
            topBranch = topBranch.getNextExpr();
         }
         write("; ");
         writeConditional(instruction.getBranchSet());
         write(";){}");

      } else if (instruction instanceof InstructionSet.CompositeForEclipseInstruction) {
         newLine();
         write("for (");
         Instruction topGoto = instruction.getFirstChild();
         if (topGoto instanceof InstructionSet.AssignToLocalVariable) {
            writeInstruction(topGoto);
            topGoto = topGoto.getNextExpr();
         }
         write("; ");
         Instruction last = instruction.getLastChild();
         while (last.getPrevExpr().isBranch()) {
            last = last.getPrevExpr();
         }
         writeConditional(instruction.getBranchSet(), true);
         write("; ");
         final Instruction delta = last.getPrevExpr();
         if (!(delta instanceof InstructionSet.CompositeInstruction)) {
            writeInstruction(delta);
            write(")");
            writeBlock(topGoto.getNextExpr(), delta);
         } else {
            write("){");
            in();
            writeSequence(topGoto.getNextExpr(), delta);

            newLine();
            writeSequence(delta, delta.getNextExpr());
            out();
            newLine();
            write("}");

         }

      } else if (instruction instanceof InstructionSet.CompositeDoWhileInstruction) {
         newLine();
         write("do");
         Instruction blockStart = instruction.getFirstChild();
         Instruction blockEnd = instruction.getLastChild();
         writeBlock(blockStart, blockEnd);
         write("while(");
         writeConditional(((InstructionSet.CompositeInstruction) instruction).getBranchSet(), true);
         write(");");
         newLine();
      }
   }

   public void writeSequence(Instruction _first, Instruction _last) throws CodeGenException {

      for (Instruction instruction = _first; instruction != _last; instruction = instruction.getNextExpr()) {
         if (instruction instanceof InstructionSet.CompositeInstruction) {
            writeComposite((InstructionSet.CompositeInstruction) instruction);
         } else if (!instruction.getByteCode().equals(InstructionSet.ByteCode.NONE)) {
            newLine();
            writeInstruction(instruction);
            write(";");

         }
      }

   }

   protected void writeGetterBlock(ClassModel.ConstantPool.FieldEntry accessorVariableFieldEntry) {
      write("{");
      in();
      newLine();
      write("return this->");
      write(accessorVariableFieldEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8());
      write(";");
      out();
      newLine();

      write("}");
   }

   public void writeBlock(Instruction _first, Instruction _last) throws CodeGenException {
      write("{");
      in();
      writeSequence(_first, _last);
      out();
      newLine();

      write("}");
   }

   public Instruction writeConditional(BranchSet _branchSet) throws CodeGenException {
      return (writeConditional(_branchSet, false));
   }

   public Instruction writeConditional(BranchSet _branchSet, boolean _invert) throws CodeGenException {

      final BranchSet.LogicalExpressionNode logicalExpression = _branchSet.getLogicalExpression();
      write(_invert ? logicalExpression : logicalExpression.cloneInverted());
      return (_branchSet.getLast().getNextExpr());
   }

   public void write(BranchSet.LogicalExpressionNode _node) throws CodeGenException {
      if (_node instanceof BranchSet.SimpleLogicalExpressionNode) {
         final BranchSet.SimpleLogicalExpressionNode sn = (BranchSet.SimpleLogicalExpressionNode) _node;

         writeConditionalBranch16((InstructionSet.ConditionalBranch16) sn.getBranch(), sn.isInvert());
      } else {
         final BranchSet.CompoundLogicalExpressionNode ln = (BranchSet.CompoundLogicalExpressionNode) _node;
         boolean needParenthesis = false;
         final BranchSet.CompoundLogicalExpressionNode parent = (BranchSet.CompoundLogicalExpressionNode) ln.getParent();
         if (parent != null) {
            if (!ln.isAnd() && parent.isAnd()) {
               needParenthesis = true;
            }
         }
         if (needParenthesis) {

            write("(");
         }
         write(ln.getLhs());
         write(ln.isAnd() ? " && " : " || ");
         write(ln.getRhs());
         if (needParenthesis) {

            write(")");
         }
      }
   }

   public String convertType(String _typeDesc, boolean useClassModel) {
      return (_typeDesc);
   }

   public String convertCast(String _cast) {
      // Strip parens off cast
      //System.out.println("cast = " + _cast);
      final String raw = convertType(_cast.substring(1, _cast.length() - 1), false);
      return ("(" + raw + ")");
   }

   public void writeInstruction(Instruction _instruction) throws CodeGenException {
      if (_instruction instanceof InstructionSet.CompositeIfElseInstruction) {
         boolean needParenthesis = isNeedParenthesis(_instruction);
         if(needParenthesis){
             write("(");
         }
         write("(");
         final Instruction lhs = writeConditional(((InstructionSet.CompositeInstruction) _instruction).getBranchSet());
         write(")?");
         writeInstruction(lhs);
         write(":");
         writeInstruction(lhs.getNextExpr().getNextExpr());
         if(needParenthesis){
             write(")");
         }
      } else if (_instruction instanceof InstructionSet.CompositeInstruction) {
         writeComposite((InstructionSet.CompositeInstruction) _instruction);

      } else if (_instruction instanceof InstructionSet.AssignToLocalVariable) {
         final InstructionSet.AssignToLocalVariable assignToLocalVariable = (InstructionSet.AssignToLocalVariable) _instruction;

         final ClassModel.LocalVariableInfo localVariableInfo = assignToLocalVariable.getLocalVariableInfo();
         if (assignToLocalVariable.isDeclaration()) {
            final String descriptor = localVariableInfo.getVariableDescriptor();
            // Arrays always map to __global arrays
            if (descriptor.startsWith("[")) {
               write(" __global ");
            }
            write(convertType(descriptor, true));
         }
         if (localVariableInfo == null) {
            throw new CodeGenException("outOfScope" + _instruction.getThisPC() + " = ");
         } else {
            write(localVariableInfo.getVariableName() + " = ");
         }

         for (Instruction operand = _instruction.getFirstChild(); operand != null; operand = operand.getNextExpr()) {
            writeInstruction(operand);
         }

      } else if (_instruction instanceof InstructionSet.AssignToArrayElement) {
         final InstructionSet.AssignToArrayElement arrayAssignmentInstruction = (InstructionSet.AssignToArrayElement) _instruction;
         writeInstruction(arrayAssignmentInstruction.getArrayRef());
         write("[");
         writeInstruction(arrayAssignmentInstruction.getArrayIndex());
         write("]");
         write(" ");
         write(" = ");
         writeInstruction(arrayAssignmentInstruction.getValue());
      } else if (_instruction instanceof InstructionSet.AccessArrayElement) {

         //we're getting an element from an array
         //if the array is a primitive then we just return the value
         //so the generated code looks like
         //arrayName[arrayIndex];
         //but if the array is an object, or multidimensional array, then we want to return
         //a pointer to our index our position in the array.  The code will look like
         //&(arrayName[arrayIndex * this->arrayNameLen_dimension]
         //
         final InstructionSet.AccessArrayElement arrayLoadInstruction = (InstructionSet.AccessArrayElement) _instruction;

         //object array, get address
         boolean isMultiDimensional = arrayLoadInstruction instanceof InstructionSet.I_AALOAD && isMultiDimensionalArray(arrayLoadInstruction);
         if (isMultiDimensional) {
            write("(&");
         }
         writeInstruction(arrayLoadInstruction.getArrayRef());
         write("[");
         writeInstruction(arrayLoadInstruction.getArrayIndex());

         //object array, find the size of each object in the array
         //for 2D arrays, this size is the size of a row.
         if (isMultiDimensional) {
            int dim = 0;
            Instruction load = arrayLoadInstruction.getArrayRef();
            while (load instanceof InstructionSet.I_AALOAD) {
               load = load.getFirstChild();
               dim++;
            }

            ClassModel.ConstantPool.NameAndTypeEntry nameAndTypeEntry = ((InstructionSet.AccessInstanceField) load).getConstantPoolFieldEntry().getNameAndTypeEntry();
            if (isMultiDimensionalArray(nameAndTypeEntry)) {
               String arrayName = nameAndTypeEntry.getNameUTF8Entry().getUTF8();
               write(" * this->" + arrayName + arrayDimMangleSuffix + dim);
            }
         }

         write("]");

         //object array, close parentheses
         if (isMultiDimensional) {
            write(")");
         }
      } else if (_instruction instanceof InstructionSet.AccessField) {
         final InstructionSet.AccessField accessField = (InstructionSet.AccessField) _instruction;
         if (accessField instanceof InstructionSet.AccessInstanceField) {
            Instruction accessInstanceField = ((InstructionSet.AccessInstanceField) accessField).getInstance();
            if (accessInstanceField instanceof InstructionSet.CloneInstruction) {
               accessInstanceField = ((InstructionSet.CloneInstruction) accessInstanceField).getReal();
            }
            if (!(accessInstanceField instanceof InstructionSet.I_ALOAD_0)) {
               writeInstruction(accessInstanceField);
               write(".");
            } else {
               writeThisRef();
            }
         }
         write(accessField.getConstantPoolFieldEntry().getNameAndTypeEntry().getNameUTF8Entry().getUTF8());

      } else if (_instruction instanceof InstructionSet.I_ARRAYLENGTH) {

         //getting the length of an array.
         //if this is a primitive array, then this is trivial
         //if we're getting an object array, then we need to find what dimension
         //we're looking at
         int dim = 0;
         Instruction load = _instruction.getFirstChild();
         while (load instanceof InstructionSet.I_AALOAD) {
            load = load.getFirstChild();
            dim++;
         }
         ClassModel.ConstantPool.NameAndTypeEntry nameAndTypeEntry = ((InstructionSet.AccessInstanceField) load).getConstantPoolFieldEntry().getNameAndTypeEntry();
         final String arrayName = nameAndTypeEntry.getNameUTF8Entry().getUTF8();
         String dimSuffix = isMultiDimensionalArray(nameAndTypeEntry) ? Integer.toString(dim) : "";
         write("this->" + arrayName + arrayLengthMangleSuffix + dimSuffix);
      } else if (_instruction instanceof InstructionSet.AssignToField) {
         final InstructionSet.AssignToField assignedField = (InstructionSet.AssignToField) _instruction;

         if (assignedField instanceof InstructionSet.AssignToInstanceField) {
            final Instruction accessInstanceField = ((InstructionSet.AssignToInstanceField) assignedField).getInstance().getReal();

            if (!(accessInstanceField instanceof InstructionSet.I_ALOAD_0)) {
               writeInstruction(accessInstanceField);
               write(".");
            } else {
               writeThisRef();
            }
         }
         write(assignedField.getConstantPoolFieldEntry().getNameAndTypeEntry().getNameUTF8Entry().getUTF8());
         write("=");
         writeInstruction(assignedField.getValueToAssign());
      } else if (_instruction instanceof InstructionSet.Constant<?>) {
         final InstructionSet.Constant<?> constantInstruction = (InstructionSet.Constant<?>) _instruction;
         final Object value = constantInstruction.getValue();

         if (value instanceof Float) {

            final Float f = (Float) value;
            if (f.isNaN()) {
               write("NAN");
            } else if (f.isInfinite()) {
               if (f < 0) {
                  write("-");
               }
               write("INFINITY");
            } else {
               write(value.toString());
               write("f");
            }
         } else if (value instanceof Double) {

            final Double d = (Double) value;
            if (d.isNaN()) {
               write("NAN");
            } else if (d.isInfinite()) {
               if (d < 0) {
                  write("-");
               }
               write("INFINITY");
            } else {
               write(value.toString());
            }
         } else {
            write(value.toString());
            if (value instanceof Long) {
               write("L");
            }
         }

      } else if (_instruction instanceof InstructionSet.AccessLocalVariable) {
         final InstructionSet.AccessLocalVariable localVariableLoadInstruction = (InstructionSet.AccessLocalVariable) _instruction;
         final ClassModel.LocalVariableInfo localVariable = localVariableLoadInstruction.getLocalVariableInfo();
         write(localVariable.getVariableName());
      } else if (_instruction instanceof InstructionSet.I_IINC) {
         final InstructionSet.I_IINC location = (InstructionSet.I_IINC) _instruction;
         final ClassModel.LocalVariableInfo localVariable = location.getLocalVariableInfo();
         final int adjust = location.getAdjust();

         write(localVariable.getVariableName());
         if (adjust == 1) {
            write("++");
         } else if (adjust == -1) {
            write("--");
         } else if (adjust > 1) {
            write("+=" + adjust);
         } else if (adjust < -1) {
            write("-=" + (-adjust));
         }
      } else if (_instruction instanceof InstructionSet.BinaryOperator) {
         final InstructionSet.BinaryOperator binaryInstruction = (InstructionSet.BinaryOperator) _instruction;
         final Instruction parent = binaryInstruction.getParentExpr();
         boolean needsParenthesis = isNeedParenthesis(binaryInstruction);

         if (needsParenthesis) {
            write("(");
         }

         writeInstruction(binaryInstruction.getLhs());

         write(" " + binaryInstruction.getOperator().getText() + " ");
         writeInstruction(binaryInstruction.getRhs());

         if (needsParenthesis) {
            write(")");
         }

      } else if (_instruction instanceof InstructionSet.CastOperator) {
         final InstructionSet.CastOperator castInstruction = (InstructionSet.CastOperator) _instruction;
         //  write("(");
         write(convertCast(castInstruction.getOperator().getText()));

         writeInstruction(castInstruction.getUnary());
         //    write(")");
      } else if (_instruction instanceof InstructionSet.UnaryOperator) {
         final InstructionSet.UnaryOperator unaryInstruction = (InstructionSet.UnaryOperator) _instruction;
         //   write("(");
         write(unaryInstruction.getOperator().getText());

         writeInstruction(unaryInstruction.getUnary());
         //   write(")");
      } else if (_instruction instanceof InstructionSet.Return) {

         final InstructionSet.Return ret = (InstructionSet.Return) _instruction;
         write("return");
         if (ret.getStackConsumeCount() > 0) {
            write("(");
            writeInstruction(ret.getFirstChild());
            write(")");
         }

      } else if (_instruction instanceof InstructionSet.MethodCall) {
         final InstructionSet.MethodCall methodCall = (InstructionSet.MethodCall) _instruction;

         final ClassModel.ConstantPool.MethodEntry methodEntry = methodCall.getConstantPoolMethodEntry();

         writeMethod(methodCall, methodEntry);
      } else if (_instruction.getByteCode().equals(InstructionSet.ByteCode.CLONE)) {
         final InstructionSet.CloneInstruction cloneInstruction = (InstructionSet.CloneInstruction) _instruction;
         writeInstruction(cloneInstruction.getReal());
      } else if (_instruction.getByteCode().equals(InstructionSet.ByteCode.INCREMENT)) {
         final InstructionSet.IncrementInstruction incrementInstruction = (InstructionSet.IncrementInstruction) _instruction;

         if (incrementInstruction.isPre()) {
            if (incrementInstruction.isInc()) {
               write("++");
            } else {
               write("--");
            }
         }

         writeInstruction(incrementInstruction.getFieldOrVariableReference());
         if (!incrementInstruction.isPre()) {
            if (incrementInstruction.isInc()) {
               write("++");
            } else {
               write("--");
            }
         }
      } else if (_instruction.getByteCode().equals(InstructionSet.ByteCode.MULTI_ASSIGN)) {
         final InstructionSet.MultiAssignInstruction multiAssignInstruction = (InstructionSet.MultiAssignInstruction) _instruction;
         InstructionSet.AssignToLocalVariable from = (InstructionSet.AssignToLocalVariable) multiAssignInstruction.getFrom();
         final InstructionSet.AssignToLocalVariable last = (InstructionSet.AssignToLocalVariable) multiAssignInstruction.getTo();
         final Instruction common = multiAssignInstruction.getCommon();
         final Stack<InstructionSet.AssignToLocalVariable> stack = new Stack<InstructionSet.AssignToLocalVariable>();

         while (from != last) {
            stack.push(from);
            from = (InstructionSet.AssignToLocalVariable) ((Instruction) from).getNextExpr();
         }

         for (InstructionSet.AssignToLocalVariable alv = stack.pop(); alv != null; alv = stack.size() > 0 ? stack.pop() : null) {

            final ClassModel.LocalVariableInfo localVariableInfo = alv.getLocalVariableInfo();
            if (alv.isDeclaration()) {
               write(convertType(localVariableInfo.getVariableDescriptor(), true));
            }
            if (localVariableInfo == null) {
               throw new CodeGenException("outOfScope" + _instruction.getThisPC() + " = ");
            } else {
               write(localVariableInfo.getVariableName() + " = ");
            }

         }
         writeInstruction(common);
      } else if (_instruction.getByteCode().equals(InstructionSet.ByteCode.INLINE_ASSIGN)) {
         final InstructionSet.InlineAssignInstruction inlineAssignInstruction = (InstructionSet.InlineAssignInstruction) _instruction;
         final InstructionSet.AssignToLocalVariable assignToLocalVariable = inlineAssignInstruction.getAssignToLocalVariable();

         final ClassModel.LocalVariableInfo localVariableInfo = assignToLocalVariable.getLocalVariableInfo();
         if (assignToLocalVariable.isDeclaration()) {
            // this is bad! we need a general way to hoist up a required declaration
            throw new CodeGenException("/* we can't declare this " + convertType(localVariableInfo.getVariableDescriptor(), true)
                  + " here */");
         }
         write(localVariableInfo.getVariableName());
         write("=");
         writeInstruction(inlineAssignInstruction.getRhs());
      } else if (_instruction.getByteCode().equals(InstructionSet.ByteCode.FIELD_ARRAY_ELEMENT_ASSIGN)) {
         final InstructionSet.FieldArrayElementAssign inlineAssignInstruction = (InstructionSet.FieldArrayElementAssign) _instruction;
         final InstructionSet.AssignToArrayElement arrayAssignmentInstruction = inlineAssignInstruction.getAssignToArrayElement();

         writeInstruction(arrayAssignmentInstruction.getArrayRef());
         write("[");
         writeInstruction(arrayAssignmentInstruction.getArrayIndex());
         write("]");
         write(" ");
         write(" = ");

         writeInstruction(inlineAssignInstruction.getRhs());
      } else if (_instruction.getByteCode().equals(InstructionSet.ByteCode.FIELD_ARRAY_ELEMENT_INCREMENT)) {

         final InstructionSet.FieldArrayElementIncrement fieldArrayElementIncrement = (InstructionSet.FieldArrayElementIncrement) _instruction;
         final InstructionSet.AssignToArrayElement arrayAssignmentInstruction = fieldArrayElementIncrement.getAssignToArrayElement();
         if (fieldArrayElementIncrement.isPre()) {
            if (fieldArrayElementIncrement.isInc()) {
               write("++");
            } else {
               write("--");
            }
         }
         writeInstruction(arrayAssignmentInstruction.getArrayRef());

         write("[");
         writeInstruction(arrayAssignmentInstruction.getArrayIndex());
         write("]");
         if (!fieldArrayElementIncrement.isPre()) {
            if (fieldArrayElementIncrement.isInc()) {
               write("++");
            } else {
               write("--");
            }
         }

      } else if (_instruction.getByteCode().equals(InstructionSet.ByteCode.NONE)) {
         // we are done
      } else if (_instruction instanceof InstructionSet.Branch) {
         throw new CodeGenException(String.format("%s -> %04d", _instruction.getByteCode().toString().toLowerCase(),
               ((InstructionSet.Branch) _instruction).getTarget().getThisPC()));
      } else if (_instruction instanceof InstructionSet.I_POP) {
         //POP discarded void call return?
         writeInstruction(_instruction.getFirstChild());
      } else {
         throw new CodeGenException(String.format("%s", _instruction.getByteCode().toString().toLowerCase()));
      }

   }

   private boolean isNeedParenthesis(Instruction instruction){
        final Instruction parent = instruction.getParentExpr();
        boolean needsParenthesis = true;

        if (parent instanceof InstructionSet.AssignToLocalVariable) {
           needsParenthesis = false;
        } else if (parent instanceof InstructionSet.AssignToField) {
           needsParenthesis = false;
        } else if (parent instanceof InstructionSet.AssignToArrayElement) {
           needsParenthesis = false;
        } else {
           /**
                       if (parent instanceof BinaryOperator) {
                          BinaryOperator parentBinaryOperator = (BinaryOperator) parent;
                          if (parentBinaryOperator.getOperator().ordinal() > binaryInstruction.getOperator().ordinal()) {
                             needsParenthesis = false;
                          }
                       }
           **/
        }
        return needsParenthesis;
   }

   private boolean isMultiDimensionalArray(ClassModel.ConstantPool.NameAndTypeEntry nameAndTypeEntry) {
      return nameAndTypeEntry.getDescriptorUTF8Entry().getUTF8().startsWith("[[");
   }

   private boolean isObjectArray(ClassModel.ConstantPool.NameAndTypeEntry nameAndTypeEntry) {
      return nameAndTypeEntry.getDescriptorUTF8Entry().getUTF8().startsWith("[L");
   }

   private boolean isMultiDimensionalArray(final InstructionSet.AccessArrayElement arrayLoadInstruction) {
      InstructionSet.AccessInstanceField accessInstanceField = getUltimateInstanceFieldAccess(arrayLoadInstruction);
      return isMultiDimensionalArray(accessInstanceField.getConstantPoolFieldEntry().getNameAndTypeEntry());
   }

   private boolean isObjectArray(final InstructionSet.AccessArrayElement arrayLoadInstruction) {
      InstructionSet.AccessInstanceField accessInstanceField = getUltimateInstanceFieldAccess(arrayLoadInstruction);
      return isObjectArray(accessInstanceField.getConstantPoolFieldEntry().getNameAndTypeEntry());
   }

   private InstructionSet.AccessInstanceField getUltimateInstanceFieldAccess(final InstructionSet.AccessArrayElement arrayLoadInstruction) {
      Instruction load = arrayLoadInstruction.getArrayRef();
      while (load instanceof InstructionSet.I_AALOAD) {
         load = load.getFirstChild();
      }

      return (InstructionSet.AccessInstanceField) load;
   }

   public void writeMethod(InstructionSet.MethodCall _methodCall, ClassModel.ConstantPool.MethodEntry _methodEntry) throws CodeGenException {
      boolean noCL = _methodEntry.getOwnerClassModel().getNoCLMethods()
            .contains(_methodEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8());
      if (noCL) {
         return;
      }

      if (_methodCall instanceof InstructionSet.VirtualMethodCall) {
         final Instruction instanceInstruction = ((InstructionSet.VirtualMethodCall) _methodCall).getInstanceReference();
         if (!(instanceInstruction instanceof InstructionSet.I_ALOAD_0)) {
            writeInstruction(instanceInstruction);
            write(".");
         } else {
            writeThisRef();
         }
      }
      final int argc = _methodEntry.getStackConsumeCount();
      write(_methodEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8());
      write("(");

      for (int arg = 0; arg < argc; arg++) {
         if (arg != 0) {
            write(", ");
         }
         writeInstruction(_methodCall.getArg(arg));
      }
      write(")");

   }

   public void writeThisRef() {
      write("this.");
   }

   public void writeMethodBody(MethodModel _methodModel) throws CodeGenException {
      if (_methodModel.isGetter() && !_methodModel.isNoCL()) {
         ClassModel.ConstantPool.FieldEntry accessorVariableFieldEntry = _methodModel.getAccessorVariableFieldEntry();
         writeGetterBlock(accessorVariableFieldEntry);
      } else {
         writeBlock(_methodModel.getExprHead(), null);
      }
   }

   public abstract void write(Entrypoint entryPoint) throws CodeGenException;
}