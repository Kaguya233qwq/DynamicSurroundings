/*
 * This file is part of Dynamic Surroundings, licensed under the MIT License (MIT).
 *
 * Copyright (c) OreCruncher
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.blockartistry.mod.DynSurround.asm;

import static org.objectweb.asm.Opcodes.ALOAD;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class PatchSoundManagerClampVolume  extends Transmorgrifier {

	public PatchSoundManagerClampVolume() {
		super("net.minecraft.client.audio.SoundManager");
	}

	@Override
	public String name() {
		return "SoundManager getNormalizedVolume";
	}

	@Override
	public boolean transmorgrify(final ClassNode cn) {
		final String[] names = { "getNormalizedVolume", "func_148594_a" };
		final String sig = "(Lnet/minecraft/client/audio/ISound;Lnet/minecraft/client/audio/SoundPoolEntry;Lnet/minecraft/client/audio/SoundCategory;)F";

		final MethodNode m = findMethod(cn, sig, names);
		if (m != null) {
			logMethod(Transformer.log(), m, "Found!");

			final String owner = "org/blockartistry/mod/DynSurround/client/sound/SoundManager";
			final String targetName = "getNormalizedVolume";

			final InsnList list = new InsnList();
			list.add(new VarInsnNode(ALOAD, 1));
			list.add(new VarInsnNode(ALOAD, 2));
			list.add(new VarInsnNode(ALOAD, 3));
			list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, targetName, sig, false));
			list.add(new InsnNode(Opcodes.FRETURN));
			m.instructions = list;

			return true;
		} else {
			Transformer.log().error("Unable to locate method {}{}", names[0], sig);
		}

		Transformer.log().info("Unable to patch [{}]!", getClassName());

		return false;
	}

}
