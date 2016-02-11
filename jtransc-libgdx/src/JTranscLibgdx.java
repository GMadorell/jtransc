/*
 * Copyright 2016 Carlos Ballesteros Velasco
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import jtransc.FastMemory;
import jtransc.JTranscEventLoop;
import jtransc.JTranscRender;
import jtransc.JTranscVersion;

import java.util.Stack;

public class JTranscLibgdx {
	static private LwjglApplication app;
	static private Runnable r_update;
	static private Runnable r_render;

	static private void init() {
		JTranscRender.impl = new LibgdxRenderer();
	}

	static public void config() {
		JTranscEventLoop.impl = new JTranscEventLoop.Impl() {

			@Override
			public void init(final Runnable init) {
				LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();

				config.width = 640;
				config.height = 480;
				config.title = "JTransc " + JTranscVersion.getVersion();
				config.stencil = 8;

				app = new LwjglApplication(new ApplicationListener() {
					@Override
					public void create() {
						JTranscLibgdx.init();

						init.run();
					}

					@Override
					public void resize(int i, int i1) {

					}

					@Override
					public void render() {
						if (r_update != null) r_update.run();
						if (r_render != null) r_render.run();
					}

					@Override
					public void pause() {

					}

					@Override
					public void resume() {

					}

					@Override
					public void dispose() {

					}
				}, config);
			}

			@Override
			public void loop(Runnable update, Runnable render) {
				JTranscLibgdx.r_update = update;
				JTranscLibgdx.r_render = render;
			}
		};
	}
}

class LibgdxRenderer implements JTranscRender.Impl {
	Stack<Integer> textureIds = new Stack<Integer>();
	com.badlogic.gdx.graphics.Texture[] textures = new com.badlogic.gdx.graphics.Texture[2048];

	public LibgdxRenderer() {
		for (int n = 0; n < 2048; n++) textureIds.add(n);
		System.out.println("LibgdxRenderer()");
	}

	@Override
	public int createTexture(String path, int width, int height) {
		int textureId = textureIds.pop();
		FileHandle fileHandle = Gdx.files.internal(path);
		System.out.println("Loading texture... " + fileHandle.file().getAbsolutePath() + ", exists: " + fileHandle.exists());
		textures[textureId] = new com.badlogic.gdx.graphics.Texture(fileHandle.file().getAbsolutePath());
		System.out.println(" ---> " + textureId);
		return textureId;
	}

	@Override
	public void disposeTexture(int textureId) {
		textures[textureId].dispose();
		textures[textureId] = null;
		textureIds.push(textureId);
	}

	private Rectangle FULL_SCISSORS = new Rectangle(0, 0, 8192, 8192);

	private boolean glEnableDisable(GL20 gl, int value, boolean enable) {
		if (enable) {
			gl.glEnable(value);
		} else {
			gl.glDisable(value);
		}
		return enable;
	}

	private ShaderProgram textureProgram;

	private ShaderProgram ShaderProgramAndCheck(String name, String vertex, String fragment) {
		ShaderProgram shader = new ShaderProgram(vertex, fragment);
		if (!shader.isCompiled()) {
			throw new RuntimeException("Shader:$name :: " + shader.getLog());
		}
		return shader;
	}

	private void initShadersOnce() {
		if (textureProgram != null) {
			return;
		}

		final String PREFIX =
			"#ifdef GL_ES\n" +
				"#define LOWP lowp\n" +
				"#define MED mediump\n" +
				"#define HIGH highp\n" +
				"precision mediump float;\n" +
				"#else\n" +
				"#define MED\n" +
				"#define LOWP\n" +
				"#define HIGH\n" +
				"#endif\n";

		textureProgram = ShaderProgramAndCheck(
			"texture",

			PREFIX +
				"uniform mat4 u_matrix;\n" +
				"attribute vec2 a_position;\n" +
				"attribute vec2 a_texcoord;\n" +
				"attribute vec4 a_color;\n" +
				"attribute vec4 a_colorOffset;\n" +
				"varying MED vec2 v_texcoord;\n" +
				"varying MED vec4 v_color;\n" +
				"varying MED vec4 v_colorOffset;\n" +
				"void main() {\n" +
				"gl_Position = u_matrix * vec4(a_position, 0, 1);\n" +
				"v_texcoord = a_texcoord;\n" +
				"v_color = a_color;\n" +
				"v_colorOffset = (a_colorOffset - vec4(0.5, 0.5, 0.5, 0.5)) * 2.0;\n" +
				"}\n",

			PREFIX +
				"uniform sampler2D u_sampler;\n" +
				"varying MED vec4 v_color;\n" +
				"varying MED vec4 v_colorOffset;\n" +
				"varying MED vec2 v_texcoord;\n" +
				"void main() {\n" +
				"gl_FragColor = texture2D(u_sampler, v_texcoord.st);\n" +
				"if (gl_FragColor.a <= 0.0) discard;\n" +
				"gl_FragColor.rgb /= gl_FragColor.a;//// alpha premultiplied is disable, we will study more in the future\n" +
				"gl_FragColor *= v_color;\n" +
				"gl_FragColor += v_colorOffset;\n" +
				"// gl_FragColor.rgb *= gl_FragColor.a;//// alpha premultiplied is disable, we will study more in the future\n" +
				"if (gl_FragColor.a <= 0.0) discard;\n" +
				"}\n"
		);
	}

	Matrix4 projection = new Matrix4();

	@Override
	public void render(FastMemory vertices, int vertexCount, short[] indices, int indexCount, int[] batches, int batchCount) {
		final GL20 gl = Gdx.gl;

		gl.glDisable(GL20.GL_STENCIL_TEST);
		gl.glDisable(GL20.GL_SCISSOR_TEST);
		gl.glClearColor(0f, 0f, 0f, 1f);
		gl.glClearStencil(0);
		gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_STENCIL_BUFFER_BIT);

		//System.out.println("indexCount:" + indexCount + ", vertexCount: " + vertexCount);

		if (indexCount == 0 || vertexCount == 0) {
			return;
		}

		Mesh mesh = new Mesh(
			Mesh.VertexDataType.VertexArray, false, vertexCount, indexCount,
			new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"),
			new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texcoord"),
			new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, "a_color"),
			new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, "a_colorOffset")
		);

		int virtualActualWidth = Gdx.graphics.getWidth();
		int virtualActualHeight = Gdx.graphics.getHeight();

		projection.setToOrtho(0f, virtualActualWidth, virtualActualHeight, 0f, 0f, 1f);

		initShadersOnce();

		mesh.setIndices(indices, 0, indexCount);
		float[] vertexData = new float[vertexCount * 6];
		for (int n = 0; n < vertexData.length; n++) vertexData[n] = vertices.getAlignedFloat32(n);
		mesh.setVertices(vertexData, 0, vertexData.length);

		//Rectangle lastClip = FULL_SCISSORS.clone();
		ShaderProgram program = this.textureProgram;

		//program.end();
		//program = createOrGetProgram(currentProgram);
		mesh.bind(program);
		program.begin();
		program.setUniformMatrix("u_matrix", projection);

		for (int batchId = 0; batchId < batchCount; batchId++) {
			int batchOffset = batchId * 16;
			int indexStart    = batches[batchOffset + 0];
			int triangleCount = batches[batchOffset + 1];
			int textureId     = batches[batchOffset + 2];
			int blendMode     = batches[batchOffset + 3];
			int maskType      = batches[batchOffset + 4];
			int stencilIndex  = batches[batchOffset + 5];
			int scissorLeft   = batches[batchOffset + 6];
			int scissorTop    = batches[batchOffset + 7];
			int scissorRight  = batches[batchOffset + 8];
			int scissorBottom = batches[batchOffset + 9];
			com.badlogic.gdx.graphics.Texture glTexture = textures[textureId];

			if (glTexture == null) {
				gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
			} else {
				glTexture.bind(0);
			}

			switch (blendMode) {
				case JTranscRender.BLEND_NORMAL  : gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
				case JTranscRender.BLEND_ADD     : gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
				default: gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
			}

			//trace('batch:' + indexStart + ',' + triangleCount);

			//gl.glDrawElements(GL20.GL_TRIANGLES, triangleCount * 3, GL20.GL_UNSIGNED_SHORT, indexStart * 2);
			mesh.render(program, GL20.GL_TRIANGLES, indexStart, triangleCount * 3, false);
		}
		program.end();

		mesh.unbind(program);

		mesh.dispose();

	}
}
