package com.atom.chat.render;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Isolated real-shader GPU cost, excluding Minecraft capture/composition and the rest of the frame. */
class PanelBlurGpuCostTest {
    @Test void measureFiveProductionKawasePasses() throws Exception {
        assumeTrue("1".equals(System.getenv("ATOMCHAT_BENCH")));
        assertTrue(GLFW.glfwInit());
        GLFW.glfwDefaultWindowHints();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long window=GLFW.glfwCreateWindow(64,64,"AtomChat blur work probe",0,0);
        assertNotEquals(0,window);
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            System.out.println("BLUR_GPU_RENDERER "+GL11C.glGetString(GL11C.GL_RENDERER));
            Path shaders=Path.of(System.getProperty("atomchat.repoRoot"),"platforms/1.21.1-fabric/src/main/resources/assets/minecraft/shaders/core");
            int vs=compile(GL20C.GL_VERTEX_SHADER,Files.readString(shaders.resolve("atomchat_kawase_blur.vsh")));
            int fs=compile(GL20C.GL_FRAGMENT_SHADER,Files.readString(shaders.resolve("atomchat_kawase_blur.fsh")));
            int program=GL20C.glCreateProgram();GL20C.glAttachShader(program,vs);GL20C.glAttachShader(program,fs);
            GL20C.glLinkProgram(program);assertEquals(GL11C.GL_TRUE,GL20C.glGetProgrami(program,GL20C.GL_LINK_STATUS));
            GL20C.glDeleteShader(vs);GL20C.glDeleteShader(fs);GL20C.glUseProgram(program);
            GL20C.glUniform1i(GL20C.glGetUniformLocation(program,"Sampler0"),0);
            int spacing=GL20C.glGetUniformLocation(program,"u_Spacing");
            int vao=GL30C.glGenVertexArrays(),vbo=GL15C.glGenBuffers();GL30C.glBindVertexArray(vao);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER,vbo);
            GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER,new float[]{-1,-1,0,1,-1,0,1,1,0,-1,-1,0,1,1,0,-1,1,0},GL15C.GL_STATIC_DRAW);
            int position=GL20C.glGetAttribLocation(program,"Position");GL20C.glEnableVertexAttribArray(position);
            GL20C.glVertexAttribPointer(position,3,GL11C.GL_FLOAT,false,12,0L);
            try {
                for(int[] size:new int[][]{{480,860},{900,1500},{1440,2160}}) {
                    int[] textures={texture(size[0],size[1]),texture(size[0],size[1])};
                    int[] fbos={framebuffer(textures[0]),framebuffer(textures[1])};
                    GL11C.glViewport(0,0,size[0],size[1]);GL11C.glDisable(GL11C.GL_BLEND);GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
                    GL11C.glClearColor(.2F,.35F,.5F,1);GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
                    long[] values=new long[100];int query=GL15C.glGenQueries();
                    try {
                        for(int frame=0;frame<125;frame++) {
                            GL15C.glBeginQuery(GL33C.GL_TIME_ELAPSED,query);
                            for(int pass=0;pass<5;pass++) {
                                GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER,fbos[(pass+1)%2]);
                                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,textures[pass%2]);
                                GL20C.glUniform1f(spacing,(2F+pass*1.5F)*2F);
                                GL11C.glDrawArrays(GL11C.GL_TRIANGLES,0,6);
                            }
                            GL15C.glEndQuery(GL33C.GL_TIME_ELAPSED);
                            // Blocking query read is confined to this benchmark; production uses its existing async ring.
                            long elapsed=GL33C.glGetQueryObjecti64(query,GL15C.GL_QUERY_RESULT);
                            if(frame>=25)values[frame-25]=elapsed;
                        }
                        Arrays.sort(values);
                        System.out.printf(Locale.ROOT,"BLUR_GPU_5PASS width=%d height=%d p50=%.4fms p95=%.4fms p99=%.4fms%n",
                                size[0],size[1],values[50]/1e6,values[95]/1e6,values[99]/1e6);
                    } finally {
                        GL15C.glDeleteQueries(query);
                        for(int f:fbos)GL30C.glDeleteFramebuffers(f);
                        for(int t:textures)GL11C.glDeleteTextures(t);
                    }
                }
            } finally {GL15C.glDeleteBuffers(vbo);GL30C.glDeleteVertexArrays(vao);GL20C.glDeleteProgram(program);}
        } finally {GLFW.glfwMakeContextCurrent(0);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    private static int compile(int type,String source) {
        int shader=GL20C.glCreateShader(type);GL20C.glShaderSource(shader,source);GL20C.glCompileShader(shader);
        assertEquals(GL11C.GL_TRUE,GL20C.glGetShaderi(shader,GL20C.GL_COMPILE_STATUS),GL20C.glGetShaderInfoLog(shader));return shader;
    }
    private static int texture(int width,int height) {
        int texture=GL11C.glGenTextures();GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,texture);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D,0,GL11C.GL_RGBA8,width,height,0,GL11C.GL_RGBA,GL11C.GL_UNSIGNED_BYTE,0L);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL11C.GL_TEXTURE_MIN_FILTER,GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL11C.GL_TEXTURE_MAG_FILTER,GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL11C.GL_TEXTURE_WRAP_S,GL12C.GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL11C.GL_TEXTURE_WRAP_T,GL12C.GL_CLAMP_TO_EDGE);return texture;
    }
    private static int framebuffer(int texture) {
        int fbo=GL30C.glGenFramebuffers();GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER,fbo);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER,GL30C.GL_COLOR_ATTACHMENT0,GL11C.GL_TEXTURE_2D,texture,0);
        assertEquals(GL30C.GL_FRAMEBUFFER_COMPLETE,GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER));return fbo;
    }
}
