from pathlib import Path
import subprocess,sys,tempfile,unittest,zipfile
ROOT=Path(__file__).resolve().parents[1]
class CraftEnginePackTest(unittest.TestCase):
    def test_pack_contains_only_enb_assets_without_server_data_and_is_reproducible(self):
        self.assertTrue((ROOT/'scripts/make_craftengine_pack.py').is_file(), 'CraftEngine pack builder is missing')
        with tempfile.TemporaryDirectory() as d:
            source=Path(d)/'source.zip'; out=Path(d)/'ce.zip'
            assets={'pack.mcmeta':b'{}','assets/extendednoteblock/models/block/c.json':b'{"model":1}', 'assets/extendednoteblock_listener/sounds.json':b'{}'}
            with zipfile.ZipFile(source,'w') as z:
                for name,data in assets.items():z.writestr(name,data)
            cmd=[sys.executable,str(ROOT/'scripts/make_craftengine_pack.py'),'--resource-pack',str(source),'--output',str(out),'--version','2.13.0']
            result=subprocess.run(cmd,capture_output=True,text=True);self.assertEqual(0,result.returncode,result.stderr)
            before=out.read_bytes();subprocess.run(cmd,check=True,capture_output=True);self.assertEqual(before,out.read_bytes())
            with zipfile.ZipFile(out) as z:
                self.assertEqual(assets['assets/extendednoteblock/models/block/c.json'],z.read('plugins/CraftEngine/resources/enb/resourcepack/assets/extendednoteblock/models/block/c.json'))
                self.assertIn('plugins/CraftEngine/resources/enb/configuration/enb.yml',z.namelist())
                self.assertFalse(any('notes.yml' in n or n.endswith('/config.yml') for n in z.namelist()))
            with zipfile.ZipFile(source,'a') as z:z.writestr('assets/private_plugin/models/x.json',b'{}')
            result=subprocess.run(cmd,capture_output=True,text=True);self.assertNotEqual(0,result.returncode)
            self.assertEqual(before,out.read_bytes(), 'Rejected input must preserve the previous output')
if __name__=='__main__':unittest.main()
