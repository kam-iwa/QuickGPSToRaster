import numpy as np

from PIL import Image
from typing import List


class RasterCreator:
    points: List[List[float]] = []
    crs: str = """GEOGCRS["WGS 84",ENSEMBLE["World Geodetic System 1984 ensemble",MEMBER["World Geodetic System 1984 (Transit)"],MEMBER["World Geodetic System 1984 (G730)"],MEMBER["World Geodetic System 1984 (G873)"],MEMBER["World Geodetic System 1984 (G1150)"],MEMBER["World Geodetic System 1984 (G1674)"],MEMBER["World Geodetic System 1984 (G1762)"],MEMBER["World Geodetic System 1984 (G2139)"],MEMBER["World Geodetic System 1984 (G2296)"],ELLIPSOID["WGS 84",6378137,298.257223563,LENGTHUNIT["metre",1]],ENSEMBLEACCURACY[2.0]],PRIMEM["Greenwich",0,ANGLEUNIT["degree",0.0174532925199433]],CS[ellipsoidal,2],AXIS["geodetic latitude (Lat)",north,ORDER[1],ANGLEUNIT["degree",0.0174532925199433]],AXIS["geodetic longitude (Lon)",east,ORDER[2],ANGLEUNIT["degree",0.0174532925199433]],USAGE[SCOPE["Horizontal component of 3D system."],AREA["World."],BBOX[-90,-180,90,180]],ID["EPSG",4326]]"""

    def __init__(self, points, output_raster_path, output_control_points_path):
        self.points = points
        self.output_raster_path = output_raster_path
        self.output_control_points_path = output_control_points_path


    def create_raster(self):
        latitudes = np.array([p[0] for p in self.points])
        longitudes = np.array([p[1] for p in self.points])
        altitudes = np.array([p[2] for p in self.points])

        lat_min, lat_max = latitudes.min(), latitudes.max()
        lon_min, lon_max = longitudes.min(), longitudes.max()

        width, height = 256, 256
        power = 2

        def gps_to_pixel(lat, lon):
            x = (lon - lon_min) / (lon_max - lon_min) * (width - 1)
            y = (lat_max - lat) / (lat_max - lat_min) * (height - 1)
            return x, y

        points_px = np.array([gps_to_pixel(lat, lon) for lat, lon, _ in self.points])
        grid = np.zeros((height, width), dtype=np.float32)

        for y in range(height):
            for x in range(width):
                dx = points_px[:, 0] - x
                dy = points_px[:, 1] - y
                dist = np.sqrt(dx ** 2 + dy ** 2)

                dist[dist == 0] = 1e-6

                weights = 1 / (dist ** power)
                grid[y, x] = np.sum(weights * altitudes) / np.sum(weights)

        grid_norm = (grid - grid.min()) / (grid.max() - grid.min())
        grid_16bit = (grid_norm * 65535).astype(np.uint16)

        image = Image.fromarray(grid_16bit)
        image.save(self.output_raster_path)

        with open(self.output_control_points_path, "w") as f:
            f.write(f"#CRS: {self.crs}\nmapX,mapY,sourceX,sourceY,enable,dX,dY,residual\n")
            f.write(f"{lon_min},{lat_max},0.00,0.00,1,0,0,0\n")
            f.write(f"{lon_max},{lat_min},255.00,-255.00,1,0,0,0\n")