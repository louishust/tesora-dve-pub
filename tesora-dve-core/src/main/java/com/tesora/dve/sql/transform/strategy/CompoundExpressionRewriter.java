package com.tesora.dve.sql.transform.strategy;

/*
 * #%L
 * Tesora Inc.
 * Database Virtualization Engine
 * %%
 * Copyright (C) 2011 - 2014 Tesora Inc.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */




import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.tesora.dve.sql.node.LanguageNode;
import com.tesora.dve.sql.node.Traversal;
import com.tesora.dve.sql.node.expression.ExpressionNode;
import com.tesora.dve.sql.node.test.EngineConstant;
import com.tesora.dve.sql.util.ListSet;

public class CompoundExpressionRewriter extends Traversal {

	protected Map<ExpressionNode, RewriterBlock> blocks; 
	protected ExpressionNode root;
	
	public CompoundExpressionRewriter(ExpressionNode r) {
		super(Order.POSTORDER,ExecStyle.ONCE);
		blocks = new HashMap<ExpressionNode,RewriterBlock>();
		root = r;
		traverse(r);
	}
	
	protected Map<ExpressionNode, RewriterBlock> getBlocks() {
		return blocks;
	}
	
	@Override
	public LanguageNode action(LanguageNode in) {
		ExpressionNode en = (ExpressionNode) in;
		RewriterBlock nb = new RewriterBlock(en);
		List<ExpressionNode> subs = en.getSubExpressions();
		if (nb.isIndependent(this)) {
			for(ExpressionNode p : subs)
				blocks.remove(p);
		}
		blocks.put(en, nb);
		return in;
	}

	public ListSet<ExpressionNode> getFirstRewriteEntries() {
		RewriterBlock rb = blocks.get(root);
		return rb.getProjectionEntries(this);
	}
	
	private static class RewriterBlock {
		
		protected ExpressionNode original;
		protected Boolean independent = null;
		
		public RewriterBlock(ExpressionNode orig) {
			original = orig;
		}
		
		private boolean computeIndependent(CompoundExpressionRewriter cer) {
			if (EngineConstant.AGGFUN.has(original))
				return false;
			List<ExpressionNode> subs = original.getSubExpressions();
			for(ExpressionNode en : subs) {
				RewriterBlock cb = cer.getBlocks().get(en);
				if (cb != null && !cb.isIndependent(cer))
					return false;
			}
			return true;			
		}
		
		public boolean isIndependent(CompoundExpressionRewriter cer) {
			if (independent == null) 
				independent = computeIndependent(cer);
			return independent.booleanValue();
		}
		
		public ListSet<ExpressionNode> getProjectionEntries(CompoundExpressionRewriter cer) {
			ListSet<ExpressionNode> out = new ListSet<ExpressionNode>();
			List<ExpressionNode> subs = original.getSubExpressions();
			if (subs.isEmpty() || isIndependent(cer) || EngineConstant.AGGFUN.has(original)) {
				out.add(original);
				return out;
			}
			for(ExpressionNode en : subs) {
				RewriterBlock rb = cer.getBlocks().get(en);
				if (rb == null) continue;
				out.addAll(rb.getProjectionEntries(cer));
			}
			return out;
		}

	}
	
}