package com.fm.logistics.service;

import com.fm.logistics.dto.NetworkTopologyDTO;
import com.fm.logistics.entity.HubLink;
import com.fm.logistics.entity.NationalHub;

import java.util.List;

public interface NationalNetworkService {

    List<NationalHub> listHubs(Integer level);

    NationalHub getHub(Long id);

    NationalHub saveHub(NationalHub hub);

    NationalHub updateHub(Long id, NationalHub hub);

    List<HubLink> listLinks();

    HubLink saveLink(HubLink link);

    NetworkTopologyDTO getTopology();
}
