import bpy, sys, os
sys.argv = [a for a in sys.argv if not a.startswith('--')]
if '--' in sys.argv:
    sys.argv = sys.argv[sys.argv.index('--')+1:]
if len(sys.argv) < 2:
    print('usage: export_basecolor.py input.glb out.png'); sys.exit(1)
src, out = sys.argv[0], sys.argv[1]
bpy.ops.object.select_all(action='SELECT'); bpy.ops.object.delete(use_global=False)
bpy.ops.import_scene.gltf(filepath=src)
img = None
for ob in bpy.context.scene.objects:
    if ob.type == 'MESH' and ob.data.materials:
        mat = ob.data.materials[0]
        if mat and mat.use_nodes:
            for node in mat.node_tree.nodes:
                if node.type == 'BSDF_PRINCIPLED':
                    sock = node.inputs.get('Base Color')
                    if sock and sock.links:
                        n = sock.links[0].from_node
                        if n.type == 'TEX_IMAGE' and n.image:
                            img = n.image; break
if img:
    img.filepath_raw = out
    img.file_format = 'PNG'
    img.save()
    print('saved', out, img.size)
else:
    print('no image')
