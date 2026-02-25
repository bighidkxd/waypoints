package com.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class WaypointRenderer {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final float[] COL_PREV    = {0.55f, 0.55f, 0.55f, 0.30f};
    private static final float[] COL_CURRENT = {0.00f, 1.00f, 0.00f, 0.65f};
    private static final float[] COL_NEXT    = {1.00f, 1.00f, 0.00f, 0.85f};
    private static final float[] COL_OTHER   = {0.50f, 0.50f, 0.50f, 0.30f};

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        WaypointState state = WaypointState.getInstance();
        if (!state.enabled || !state.hasGroup()) return;

        tickAdvance(state);

        double vx = mc.getRenderManager().viewerPosX;
        double vy = mc.getRenderManager().viewerPosY;
        double vz = mc.getRenderManager().viewerPosZ;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glLineWidth(2.0f);
        GL11.glDisable(GL11.GL_CULL_FACE);

        GL11.glPushMatrix();
        GL11.glTranslated(-vx, -vy, -vz);
        if (state.setupMode) drawSetupBoxes(state);
        else                 drawNormalBoxes(state);
        GL11.glPopMatrix();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        GL11.glPushMatrix();
        GL11.glTranslated(-vx, -vy, -vz);
        if (state.setupMode) drawSetupLabels(state);
        else                 drawNormalLabels(state);
        GL11.glPopMatrix();

        GL11.glPopAttrib();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private void tickAdvance(WaypointState state) {
        if (mc.thePlayer == null) return;
        WaypointPoint next = state.getNext();
        if (next == null) return;

        double dist = next.distanceTo(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);

        if (dist <= state.advanceRange) {
            if (state.advanceTimerStart < 0) {
                state.advanceTimerStart = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - state.advanceTimerStart >= state.advanceDelayMs) {
                state.advance();
            }
        } else {
            state.advanceTimerStart = -1L;
        }
    }

    private void drawSetupBoxes(WaypointState state) {
        WaypointPoint nxt = state.getNext();
        if (nxt == null) return;

        drawEspBox(nxt.x, nxt.y, nxt.z, COL_NEXT[0], COL_NEXT[1], COL_NEXT[2], COL_NEXT[3]);
        drawTracer(nxt.x + 0.5, nxt.y + 0.5, nxt.z + 0.5);
    }

    private void drawNormalBoxes(WaypointState state) {
        WaypointPoint nxt = state.getNext();
        if (nxt == null) return;

        drawEspBox(nxt.x, nxt.y, nxt.z, COL_NEXT[0], COL_NEXT[1], COL_NEXT[2], COL_NEXT[3]);
        drawTracer(nxt.x + 0.5, nxt.y + 0.5, nxt.z + 0.5);
    }

    private void drawSetupLabels(WaypointState state) {
        List<WaypointPoint> wps = state.loadedGroup.waypoints;
        int cur = state.currentIndex;
        int nxt = state.getNextIndex();
        for (int i = 0; i < wps.size(); i++) {
            WaypointPoint wp = wps.get(i);
            String col   = (i == cur) ? "\u00a7a" : (i == nxt) ? "\u00a7e" : "\u00a77";
            String label = (wp.name != null && !wp.name.isEmpty()) ? wp.name : String.valueOf(i + 1);
            drawLabel(wp.x + 0.5, wp.y + 2.2, wp.z + 0.5, col + label);
        }
    }

    private void drawNormalLabels(WaypointState state) {
        WaypointPoint prev = state.getPrev();
        WaypointPoint cur  = state.getCurrent();
        WaypointPoint nxt  = state.getNext();

        if (prev != null && prev != cur)
            drawLabel(prev.x + 0.5, prev.y + 2.2, prev.z + 0.5, "\u00a77" + safeName(prev, "Prev"));
        if (cur != null)
            drawLabel(cur.x  + 0.5, cur.y  + 2.2, cur.z  + 0.5, "\u00a7a" + safeName(cur,  "Current"));
        if (nxt != null && nxt != cur)
            drawLabel(nxt.x  + 0.5, nxt.y  + 2.2, nxt.z  + 0.5, "\u00a7e" + safeName(nxt,  "Next"));
    }

    private String safeName(WaypointPoint wp, String fallback) {
        return (wp.name != null && !wp.name.isEmpty()) ? wp.name : fallback;
    }

    private void drawEspBox(double x, double y, double z,
                            float r, float g, float b, float a) {

        final double[][] edges = {
                {0,0,0, 1,0,0}, {0,0,1, 1,0,1}, {0,0,0, 0,0,1}, {1,0,0, 1,0,1},
                {0,1,0, 1,1,0}, {0,1,1, 1,1,1}, {0,1,0, 0,1,1}, {1,1,0, 1,1,1},
                {0,0,0, 0,1,0}, {1,0,0, 1,1,0}, {0,0,1, 0,1,1}, {1,0,1, 1,1,1}
        };

        int ri = (int)(r * 255), gi = (int)(g * 255), bi = (int)(b * 255), ai = (int)(a * 255);

        double ox = x;
        double oz = z;

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (double[] e : edges) {
            wr.pos(ox + e[0], y + e[1], oz + e[2]).color(ri, gi, bi, ai).endVertex();
            wr.pos(ox + e[3], y + e[4], oz + e[5]).color(ri, gi, bi, ai).endVertex();
        }
        tess.draw();
    }

    private void drawTracer(double x, double y, double z) {
        if (mc.thePlayer == null) return;

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glLineWidth(2.5f);

        double px = mc.thePlayer.posX;
        double py = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double pz = mc.thePlayer.posZ;

        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(px, py, pz).color(255, 255, 0, 255).endVertex();
        wr.pos(x, y, z).color(255, 255, 0, 255).endVertex();
        tess.draw();
    }

    private void drawLabel(double wx, double wy, double wz, String text) {
        if (mc.thePlayer == null || mc.fontRendererObj == null) return;

        double dx = wx - mc.thePlayer.posX;
        double dy = wy - mc.thePlayer.posY;
        double dz = wz - mc.thePlayer.posZ;
        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);

        double renderDist = Math.min(dist, 50.0);
        float scale = Math.max(0.025f, (float)(renderDist / 300.0));

        String full = text + " \u00a7b" + (int) Math.round(dist) + "m";
        int width = mc.fontRendererObj.getStringWidth(full);

        GL11.glPushMatrix();
        GL11.glTranslated(wx, wy, wz);
        GL11.glRotatef(-mc.getRenderManager().playerViewY, 0f, 1f, 0f);
        GL11.glRotatef(mc.getRenderManager().playerViewX, 1f, 0f, 0f);
        GL11.glScalef(-scale, -scale, scale);

        GL11.glColor4f(1f, 1f, 1f, 1f);

        mc.fontRendererObj.drawStringWithShadow(full, -width / 2f, 0f, 0xFFFFFF);

        GL11.glPopMatrix();
    }
}