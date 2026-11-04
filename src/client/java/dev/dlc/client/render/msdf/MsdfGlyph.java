package dev.dlc.client.render.msdf;


import dev.dlc.client.render.msdf.FontData.BoundsData;
import dev.dlc.client.render.msdf.FontData.GlyphData;
import com.mojang.blaze3d.vertex.VertexConsumer;

public final class MsdfGlyph {

	private final int code;
	private final float minU, maxU, minV, maxV;
	private final float advance, topPosition, width, height;
	
	public MsdfGlyph(GlyphData data, float atlasWidth, float atlasHeight) {
		this.code = data.unicode();
		this.advance = data.advance();
		
		BoundsData atlasBounds = data.atlasBounds();
		if (atlasBounds != null) {
			this.minU = atlasBounds.left() / atlasWidth;
			this.maxU = atlasBounds.right() / atlasWidth;
			this.minV = 1.0F - atlasBounds.top() / atlasHeight;
			this.maxV = 1.0F - atlasBounds.bottom() / atlasHeight;
		} else {
			this.minU = this.maxU = this.minV = this.maxV = 0.0f;
		}

		BoundsData planeBounds = data.planeBounds();
		if (planeBounds != null) {
			this.width = planeBounds.right() - planeBounds.left();
			this.height = planeBounds.top() - planeBounds.bottom();
			this.topPosition = planeBounds.top();
		} else {
			this.width = this.height = this.topPosition = 0.0f;
		}
	}
	
	public float apply(VertexConsumer consumer, float size, float x, float y, float z, int color) {
		y -= this.topPosition * size;
		float width = this.width * size;
		float height = this.height * size;
		consumer.addVertex(x, y, z).setUv(this.minU, this.minV).setColor(color);
		consumer.addVertex(x, y + height, z).setUv(this.minU, this.maxV).setColor(color);
		consumer.addVertex(x + width, y + height, z).setUv(this.maxU, this.maxV).setColor(color);
		consumer.addVertex(x + width, y, z).setUv(this.maxU, this.minV).setColor(color);
		
		return this.advance * size;
	}
	
	public float getWidth(float size) {
		return this.advance * size;
	}

	public float topFromBaseline() {
		return -topPosition;
	}

	public float bottomFromBaseline() {
		return height - topPosition;
	}

	public int getCharCode() {
		return code;
	}

}