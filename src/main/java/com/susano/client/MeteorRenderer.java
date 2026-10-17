package com.susano.client;

import com.susano.MeteorEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Dessine la meteorite : rocher irregulier qui tourne, faces incandescentes, halo de chaleur. */
public class MeteorRenderer extends EntityRenderer<MeteorEntity> {
    private static final Identifier ROCK = new Identifier("textures/block/blackstone.png");
    private static final Identifier WHITE = new Identifier("textures/block/white_concrete.png");

    private static final float[][] V;
    private static final int[][] F;

    static {
        float t = (1f + (float) Math.sqrt(5.0)) / 2f;
        float[][] base = {{-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0}, {0, -1, t}, {0, 1, t},
                {0, -1, -t}, {0, 1, -t}, {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1}};
        int[][] faces = {{0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2},
                {10, 7, 6}, {7, 1, 8}, {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11},
                {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};
        List<float[]> verts = new ArrayList<>();
        for (float[] b : base) {
            verts.add(norm(b));
        }
        List<int[]> out = new ArrayList<>();
        Map<Long, Integer> mid = new HashMap<>();
        for (int[] f : faces) {
            int a = mid(verts, mid, f[0], f[1]);
            int b = mid(verts, mid, f[1], f[2]);
            int c = mid(verts, mid, f[2], f[0]);
            out.add(new int[]{f[0], a, c});
            out.add(new int[]{f[1], b, a});
            out.add(new int[]{f[2], c, b});
            out.add(new int[]{a, b, c});
        }
        V = new float[verts.size()][];
        for (int i = 0; i < V.length; i++) {
            float[] d = verts.get(i);
            // rocher irregulier : rayon different par sommet (bruit deterministe)
            double n = Math.sin(d[0] * 12.9898 + d[1] * 78.233 + d[2] * 37.719) * 43758.5453;
            float k = (float) (n - Math.floor(n));
            float r = 0.76f + 0.24f * k;
            V[i] = new float[]{d[0] * r, d[1] * r, d[2] * r};
        }
        F = out.toArray(new int[0][]);
    }

    private static float[] norm(float[] v) {
        float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new float[]{v[0] / l, v[1] / l, v[2] / l};
    }

    private static int mid(List<float[]> verts, Map<Long, Integer> cache, int i, int j) {
        long key = ((long) Math.min(i, j) << 32) | Math.max(i, j);
        Integer found = cache.get(key);
        if (found != null) {
            return found;
        }
        float[] a = verts.get(i), b = verts.get(j);
        verts.add(norm(new float[]{(a[0] + b[0]) / 2f, (a[1] + b[1]) / 2f, (a[2] + b[2]) / 2f}));
        int idx = verts.size() - 1;
        cache.put(key, idx);
        return idx;
    }

    public MeteorRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public Identifier getTexture(MeteorEntity entity) {
        return ROCK;
    }

    @Override
    public boolean shouldRender(MeteorEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(MeteorEntity entity, float yaw, float td, MatrixStack ms, VertexConsumerProvider vcp, int light) {
        float size = entity.getSize();
        float age = entity.age + td;
        int bl = Math.max(LightmapTextureManager.getBlockLightCoordinates(light), 8);
        int rockLight = LightmapTextureManager.pack(bl, LightmapTextureManager.getSkyLightCoordinates(light));
        int full = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        ms.push();
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(age * 9f));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(age * 6f));
        ms.scale(size, size, size);
        MatrixStack.Entry en = ms.peek();

        if (entity.getStyle() == 1) {
            // sphere de Chibaku Tensei : roche grise, halo violet sombre
            mesh(vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(ROCK)), en, 1f, 0, age, 190, 190, 200, 255, rockLight);
            mesh(vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE)), en, 1.12f, 0, age, 90, 30, 150, 60, full);
            mesh(vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE)), en, 1.3f, 0, age, 40, 10, 80, 40, full);
        } else {
            // 1) la roche sombre
            mesh(vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(ROCK)), en, 1f, 0, age, 150, 115, 105, 255, rockLight);
            // 2) faces incandescentes (fissures de lave)
            mesh(vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE)), en, 1.015f, 1, age, 255, 110, 20, 230, full);
            // 3) halo de chaleur
            mesh(vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE)), en, 1.22f, 0, age, 255, 120, 30, 70, full);
            mesh(vcp.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE)), en, 1.5f, 0, age, 255, 60, 10, 32, full);
        }
        ms.pop();
    }

    /** mode 0 : toutes les faces ; mode 1 : seulement les faces incandescentes. */
    private static void mesh(VertexConsumer vc, MatrixStack.Entry en, float scale, int mode, float age,
                             int r, int g, int b, int a, int light) {
        Matrix4f pm = en.getPositionMatrix();
        Matrix3f nm = en.getNormalMatrix();
        for (int i = 0; i < F.length; i++) {
            boolean ember = (i * 7 + 3) % 11 < 3;
            if (mode == 1 && !ember) {
                continue;
            }
            float[] p0 = V[F[i][0]], p1 = V[F[i][1]], p2 = V[F[i][2]];
            float ux = p1[0] - p0[0], uy = p1[1] - p0[1], uz = p1[2] - p0[2];
            float vx = p2[0] - p0[0], vy = p2[1] - p0[1], vz = p2[2] - p0[2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1.0e-6f) {
                continue;
            }
            nx /= l;
            ny /= l;
            nz /= l;
            int gg = g;
            if (mode == 1) {
                gg = (int) Math.min(255, g + 70 * (0.5 + 0.5 * Math.sin(age * 0.5 + i)));
            }
            put(vc, pm, nm, p0, scale, 0f, 1f, nx, ny, nz, r, gg, b, a, light);
            put(vc, pm, nm, p1, scale, 1f, 1f, nx, ny, nz, r, gg, b, a, light);
            put(vc, pm, nm, p2, scale, 0.5f, 0f, nx, ny, nz, r, gg, b, a, light);
            put(vc, pm, nm, p2, scale, 0.5f, 0f, nx, ny, nz, r, gg, b, a, light);
        }
    }

    private static void put(VertexConsumer vc, Matrix4f pm, Matrix3f nm, float[] p, float s, float u, float v,
                            float nx, float ny, float nz, int r, int g, int b, int a, int light) {
        vc.vertex(pm, p[0] * s, p[1] * s, p[2] * s)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(nm, nx, ny, nz)
                .next();
    }
}
